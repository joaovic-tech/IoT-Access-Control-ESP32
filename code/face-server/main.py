"""Servidor de reconhecimento facial do IoT Access Control (FastAPI + OpenCV).

Rotas principais (contrato usado pelo app Kotlin e pelo ESP32-CAM):

    GET    /health                     estado do servidor e do motor facial
    POST   /reload                     recarrega os encodings de known_faces/
    POST   /detect                     imagem multipart vinda do ESP32 (PIR/scan)
    POST   /scan                       puxa /capture do ESP32 e roda /detect
    GET    /detections                 historico paginado das deteccoes
    GET    /people                     lista de pessoas cadastradas
    POST   /people                     cadastro com 3-5 amostras (multipart)
    PATCH  /people/{id}                atualiza autorizacao/categoria/observacao
    DELETE /people/{id}                remove pessoa e amostras
    GET    /people/{id}/samples/{n}    devolve a n-esima amostra (JPEG)
    POST   /people/{id}/samples        adiciona amostras a uma pessoa existente
    POST   /enroll/from-esp32          captura no ESP32 e usa como amostra

Executar:  python -m uvicorn main:app --host 0.0.0.0 --port 8000
"""
from __future__ import annotations

import time
from contextlib import asynccontextmanager
from typing import Any

import requests
from fastapi import Body, FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

import detections_store
import esp32_client
import face_engine
import notifier
import people_store
from config import (
    AUTO_UNLOCK,
    DEFAULT_ROTATION,
    DETECTIONS_DIR,
    ESP32_DEFAULT_URL,
    HOST,
    MIN_SAMPLES_ACTIVE,
    PORT,
    TELEGRAM_ENABLED,
    TOLERANCE,
    ensure_dirs,
)

START_TIME = time.time()
MAX_SAMPLES_PER_REQUEST = 5

@asynccontextmanager
async def lifespan(_: FastAPI):
    ensure_dirs()
    print("=" * 58)
    print(" Face Server - IoT Access Control")
    print(f" Tolerancia: {TOLERANCE} | Auto-unlock: {AUTO_UNLOCK}")
    print(f" ESP32 padrao: {ESP32_DEFAULT_URL}")
    if face_engine.ENGINE_AVAILABLE:
        face_engine.load_known_faces()
    else:
        print(f" [AVISO] Motor facial indisponivel: {face_engine.ENGINE_ERROR}")
    print("=" * 58)
    yield


app = FastAPI(
    title="IoT Access Control - Face Server",
    description="Reconhecimento facial para o ESP32-CAM e o app Kotlin.",
    version="1.0.0",
    lifespan=lifespan,
)

# O app Android e o painel embarcado do ESP32 vivem na mesma LAN.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

ensure_dirs()
app.mount("/media/detections", StaticFiles(directory=DETECTIONS_DIR), name="detections")


# ==========================================================================
# Helpers
# ==========================================================================
def _require_engine() -> None:
    if not face_engine.ENGINE_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail=(
                "Motor de reconhecimento indisponivel. Instale as dependencias "
                f"(requirements.txt). Erro: {face_engine.ENGINE_ERROR}"
            ),
        )


def _resolve_person(person_id: str) -> tuple[str, bool]:
    person = people_store.get_person(person_id)
    if not person:
        return "Desconhecido", False
    return person.name, person.authorized


def _log_access(record: dict[str, Any]) -> None:
    """Log estruturado de tentativa de acesso (CLAUDE.md 4.3 / tasks.md E3)."""
    print(
        "[ACESSO] status={status} pessoa={person} autorizado={authorized} "
        "origem={source} distancia={distance} destravado={unlocked} "
        "arquivo={file}".format(**record)
    )


def _run_pipeline(
    image: bytes,
    source: str,
    esp32_url: str | None = None,
    rotation: int | None = None,
) -> dict[str, Any]:
    """Reconhece, grava a deteccao e (opcionalmente) libera o acesso.

    `rotation` gira o quadro antes do reconhecimento (camera montada de lado ou
    invertida). A imagem gravada em detections/ ja sai girada.
    """
    _require_engine()
    degrees = DEFAULT_ROTATION if rotation is None else rotation
    try:
        image = face_engine.rotate_jpeg(image, degrees)
        status, matches = face_engine.identify(image, _resolve_person)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    # Prioriza o rosto reconhecido mais proximo para nomear a deteccao.
    best = None
    for match in matches:
        if match.person_id and (best is None or match.distance < best.distance):
            best = match

    person_name = best.name if best else ("Desconhecido" if matches else "")
    person_id = best.person_id if best else None
    authorized = bool(best and best.authorized)
    category = ""
    if person_id:
        person = people_store.get_person(person_id)
        category = person.category if person else ""

    unlocked = False
    if authorized and AUTO_UNLOCK:
        unlocked = esp32_client.unlock(esp32_url or ESP32_DEFAULT_URL)

    record = detections_store.save(
        image=image,
        status=status,
        person=person_name,
        person_id=person_id,
        category=category,
        authorized=authorized,
        distance=best.distance if best else None,
        source=source,
        unlocked=unlocked,
    )
    _log_access(record)

    if TELEGRAM_ENABLED and status != "no_face":
        caption = (
            f"{person_name or 'Rosto desconhecido'} - {record['time_label']} "
            f"({'autorizado' if authorized else 'nao autorizado'})"
        )
        notifier.send_photo(DETECTIONS_DIR / record["file"], caption)

    return {
        "status": status,
        "detected": [
            {
                "person_id": m.person_id,
                "name": m.name,
                "authorized": m.authorized,
                "distance": m.distance,
                "box": m.box,
            }
            for m in matches
        ],
        "file": record["file"],
        "url": record["url"],
        "unlocked": unlocked,
        "detection": record,
    }


# ==========================================================================
# Saude / manutencao
# ==========================================================================
@app.get("/health")
def health() -> dict[str, Any]:
    stats = face_engine.stats()
    return {
        "status": "ok" if face_engine.ENGINE_AVAILABLE else "degraded",
        "known_people": stats["people"],
        "known_samples": stats["samples"],
        "uptime_s": round(time.time() - START_TIME, 1),
        "engine_available": face_engine.ENGINE_AVAILABLE,
        "engine_error": face_engine.ENGINE_ERROR,
        "tolerance": TOLERANCE,
        "auto_unlock": AUTO_UNLOCK,
    }


@app.post("/reload")
def reload_faces() -> dict[str, Any]:
    _require_engine()
    stats = face_engine.load_known_faces()
    return {
        "reloaded": True,
        "known_people": stats["people"],
        "known_samples": stats["samples"],
    }


# ==========================================================================
# Deteccao
# ==========================================================================
@app.post("/detect")
async def detect(
    file: UploadFile = File(..., description="JPEG capturado pelo ESP32-CAM"),
    source: str = Form("esp32"),
    rotation: int | None = Form(None),
) -> dict[str, Any]:
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Arquivo vazio")
    # Sem `rotation` (caso do firmware no evento do PIR) vale o ROTATION do .env.
    return _run_pipeline(data, source=source, rotation=rotation)


class ScanRequest(BaseModel):
    esp32_url: str | None = None
    source: str = "scan"
    # O app manda a orientacao cadastrada para o dispositivo selecionado.
    rotation: int | None = None


@app.post("/scan")
def scan(payload: ScanRequest = Body(default=ScanRequest())) -> dict[str, Any]:
    """Puxa um frame do ESP32-CAM e roda o mesmo pipeline do /detect."""
    esp32_url = (payload.esp32_url or ESP32_DEFAULT_URL).rstrip("/")
    try:
        image = esp32_client.capture(esp32_url)
    except requests.RequestException as exc:
        raise HTTPException(
            status_code=502, detail=f"Falha ao capturar em {esp32_url}: {exc}"
        ) from exc
    return _run_pipeline(
        image,
        source=payload.source,
        esp32_url=esp32_url,
        rotation=payload.rotation,
    )


@app.get("/detections")
def list_detections(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
) -> dict[str, Any]:
    items, total = detections_store.list_recent(limit=limit, offset=offset)
    return {"total": total, "limit": limit, "offset": offset, "items": items}


# ==========================================================================
# CRUD de pessoas
# ==========================================================================
@app.get("/people")
def get_people() -> dict[str, Any]:
    return {"people": [p.to_dict() for p in people_store.list_people()]}


def _store_samples(
    person: people_store.Person,
    files: list[UploadFile],
    datas: list[bytes],
) -> list[str]:
    """Valida (1 rosto por amostra) e grava as amostras. Retorna avisos."""
    warnings: list[str] = []
    for upload, data in zip(files, datas):
        if not data:
            warnings.append(f"{upload.filename}: arquivo vazio")
            continue
        try:
            faces = face_engine.count_faces(data)
        except ValueError as exc:
            warnings.append(f"{upload.filename}: {exc}")
            continue
        if faces != 1:
            warnings.append(
                f"{upload.filename}: {faces} rosto(s) encontrados (esperado exatamente 1)"
            )
            continue
        people_store.add_sample_bytes(person, data)
    return warnings


@app.post("/people", status_code=201)
async def create_person(
    name: str = Form(...),
    category: str = Form("Visitante"),
    authorized: bool = Form(False),
    note: str = Form(""),
    files: list[UploadFile] = File(default=[], description="3 a 5 amostras do rosto"),
) -> dict[str, Any]:
    _require_engine()
    if not name.strip():
        raise HTTPException(status_code=400, detail="Nome obrigatorio")
    if len(files) > MAX_SAMPLES_PER_REQUEST:
        raise HTTPException(
            status_code=400,
            detail=f"Maximo de {MAX_SAMPLES_PER_REQUEST} amostras por requisicao",
        )

    datas = [await f.read() for f in files]
    person = people_store.create_person(
        name=name, category=category, authorized=authorized, note=note
    )
    warnings = _store_samples(person, files, datas)
    stats = face_engine.load_known_faces()

    return {
        "person": person.to_dict(),
        "warnings": warnings,
        "min_samples_active": MIN_SAMPLES_ACTIVE,
        "known_people": stats["people"],
        "known_samples": stats["samples"],
    }


class PersonPatch(BaseModel):
    name: str | None = None
    category: str | None = None
    authorized: bool | None = None
    note: str | None = None


@app.patch("/people/{person_id}")
def patch_person(person_id: str, patch: PersonPatch) -> dict[str, Any]:
    person = people_store.update_person(person_id, **patch.model_dump(exclude_unset=True))
    if not person:
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    return {"person": person.to_dict()}


@app.delete("/people/{person_id}")
def remove_person(person_id: str) -> dict[str, Any]:
    if not people_store.delete_person(person_id):
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    stats = (
        face_engine.load_known_faces()
        if face_engine.ENGINE_AVAILABLE
        else face_engine.stats()
    )
    return {
        "deleted": True,
        "known_people": stats["people"],
        "known_samples": stats["samples"],
    }


@app.get("/people/{person_id}/samples/{index}")
def get_sample(person_id: str, index: int) -> FileResponse:
    person = people_store.get_person(person_id)
    if not person:
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    files = person.sample_files()
    if index < 1 or index > len(files):
        raise HTTPException(status_code=404, detail="Amostra nao encontrada")
    return FileResponse(files[index - 1], media_type="image/jpeg")


@app.post("/people/{person_id}/samples")
async def add_samples(
    person_id: str,
    files: list[UploadFile] = File(...),
) -> dict[str, Any]:
    _require_engine()
    person = people_store.get_person(person_id)
    if not person:
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    if len(files) > MAX_SAMPLES_PER_REQUEST:
        raise HTTPException(
            status_code=400,
            detail=f"Maximo de {MAX_SAMPLES_PER_REQUEST} amostras por requisicao",
        )
    datas = [await f.read() for f in files]
    warnings = _store_samples(person, files, datas)
    face_engine.load_known_faces()
    updated = people_store.get_person(person_id)
    return {"person": updated.to_dict() if updated else None, "warnings": warnings}


class EnrollRequest(BaseModel):
    person_id: str
    esp32_url: str | None = None
    rotation: int | None = None


@app.post("/enroll/from-esp32")
def enroll_from_esp32(payload: EnrollRequest) -> dict[str, Any]:
    """Captura um frame no ESP32-CAM e usa como amostra da pessoa informada."""
    _require_engine()
    person = people_store.get_person(payload.person_id)
    if not person:
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")

    esp32_url = (payload.esp32_url or ESP32_DEFAULT_URL).rstrip("/")
    try:
        image = esp32_client.capture(esp32_url)
    except requests.RequestException as exc:
        raise HTTPException(
            status_code=502, detail=f"Falha ao capturar em {esp32_url}: {exc}"
        ) from exc

    # Gira antes de validar: a amostra precisa ser gravada na mesma orientacao
    # em que os rostos serao reconhecidos depois.
    degrees = DEFAULT_ROTATION if payload.rotation is None else payload.rotation
    try:
        image = face_engine.rotate_jpeg(image, degrees)
        faces = face_engine.count_faces(image)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if faces != 1:
        raise HTTPException(
            status_code=422,
            detail=f"A captura tem {faces} rosto(s); e necessario exatamente 1",
        )

    people_store.add_sample_bytes(person, image)
    face_engine.load_known_faces()
    updated = people_store.get_person(payload.person_id)
    return {"person": updated.to_dict() if updated else None}


@app.get("/")
def root() -> JSONResponse:
    return JSONResponse(
        {
            "service": "IoT Access Control - Face Server",
            "docs": "/docs",
            "health": "/health",
        }
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host=HOST, port=PORT, reload=False)
