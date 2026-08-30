"""Servidor de reconhecimento facial do IoT Access Control (FastAPI + OpenCV).

Rotas principais (contrato usado pelo app Kotlin e pelo ESP32-CAM):

    GET    /health                     estado do servidor e do motor facial
    POST   /reload                     recarrega os encodings de known_faces/
    POST   /detect                     imagem multipart vinda do ESP32 (PIR/scan)
    POST   /scan                       puxa /capture do ESP32 e roda /detect
    GET    /detections                 historico paginado das deteccoes
    GET    /rooms                      comodos cadastrados (CRUD completo)
    GET    /categories                 catalogo de categorias/vinculos
    GET    /people                     lista de pessoas cadastradas
    POST   /people                     cadastro com 3-5 amostras (multipart)
    PATCH  /people/{id}                nome, categoria, comodos e autorizacao
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

import access_control
import assistant
import detections_store
import esp32_client
import face_engine
import liveness
import notifier
import presence
import people_store
import rooms_store
from config import (
    AUTO_UNLOCK,
    DEFAULT_ROOM,
    LIVENESS_ENABLED,
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
    if LIVENESS_ENABLED:
        liveness.load_model()
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
    """Log estruturado de tentativa de acesso (CLAUDE.md 4.3)."""
    print(
        "[ACESSO] status={status} pessoa={person} autorizado={authorized} "
        "comodo={room} permitido_no_comodo={access_allowed} "
        "origem={source} distancia={distance} destravado={unlocked} "
        "arquivo={file}".format(**record)
    )


def _run_pipeline(
    image: bytes,
    source: str,
    esp32_url: str | None = None,
    rotation: int | None = None,
    room: str | None = None,
    room_id: str | None = None,
) -> dict[str, Any]:
    """Reconhece, grava a deteccao e (opcionalmente) libera o acesso.

    `rotation` gira o quadro antes do reconhecimento (camera montada de lado ou
    invertida). A imagem gravada em detections/ ja sai girada.

    `room` e o comodo vigiado pela camera. Com ele o servidor confronta a regra
    de acesso da pessoa (interpretada da observacao de cadastro) e nega o
    acesso de quem esta no lugar errado, mesmo estando autorizada.
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
    # Vivacidade (G1): so faz sentido quando ha rosto reconhecido -- e o unico
    # caso em que um ataque de foto renderia acesso.
    vivacidade: dict[str, Any] = {"vivo": None, "score": None, "limiar": None}
    if LIVENESS_ENABLED and best is not None:
        try:
            vivacidade = liveness.check(face_engine.decode_rgb(image), best.box)
        except Exception as exc:  # nunca derruba a deteccao
            print(f"[VIVACIDADE] falha na verificacao: {exc}")

    # O app manda `room_id`; o firmware nao manda nada e vale o ROOM do .env,
    # casado pelo nome (F5).
    comodo_texto = (room or "").strip() or DEFAULT_ROOM
    comodo = rooms_store.resolve(room_id=room_id, name=comodo_texto)
    nome_comodo = comodo.name if comodo else comodo_texto

    category = ""
    access_allowed: bool | None = None
    access_reason = ""
    if person_id:
        person = people_store.get_person(person_id)
        if person:
            category = person.category
            access_allowed, access_reason = access_control.evaluate(person, comodo)

    # A decisao por comodo so restringe: nunca autoriza quem nao esta autorizado.
    if access_allowed is False:
        authorized = False

    # Foto impressa ou tela: nega o acesso mesmo com o rosto batendo. `vivo`
    # None significa verificacao desligada -- nada muda (falha aberta).
    if vivacidade["vivo"] is False:
        authorized = False
        print(f"[VIVACIDADE] possivel ataque de apresentacao: {person_name}")

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
        room=nome_comodo,
        room_id=comodo.id if comodo else None,
        access_allowed=access_allowed,
        access_reason=access_reason,
        live=vivacidade["vivo"],
        live_score=vivacidade["score"],
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
        "room": DEFAULT_ROOM,
        "rooms": len(rooms_store.list_rooms()),
        "presence": presence.stats(),
        "liveness": liveness.stats(),
        "assistant": assistant.stats(),
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
    room: str = Form(""),
    room_id: str = Form(""),
) -> dict[str, Any]:
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Arquivo vazio")
    # Sem `rotation`/`room` (caso do firmware no evento do PIR) valem o ROTATION
    # e o ROOM do .env.
    return _run_pipeline(
        data, source=source, rotation=rotation, room=room, room_id=room_id or None
    )


class ScanRequest(BaseModel):
    esp32_url: str | None = None
    source: str = "scan"
    # O app manda a orientacao cadastrada para o dispositivo selecionado.
    rotation: int | None = None
    # Comodo vigiado pelo dispositivo. O app manda o id cadastrado; o texto
    # continua aceito e e casado pelo nome (compatibilidade).
    room: str = ""
    room_id: str | None = None


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
        room=payload.room,
        room_id=payload.room_id,
    )


@app.get("/detections")
def list_detections(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
) -> dict[str, Any]:
    items, total = detections_store.list_recent(limit=limit, offset=offset)
    return {"total": total, "limit": limit, "offset": offset, "items": items}


# ==========================================================================
# Comodos e niveis de acesso (F1 / F2)
# ==========================================================================
class RoomIn(BaseModel):
    name: str


@app.get("/rooms")
def get_rooms() -> dict[str, Any]:
    return {"rooms": [r.to_dict() for r in rooms_store.list_rooms()]}


@app.post("/rooms", status_code=201)
def create_room(payload: RoomIn) -> dict[str, Any]:
    if not payload.name.strip():
        raise HTTPException(status_code=400, detail="Nome do comodo obrigatorio")
    if rooms_store.find_by_name(payload.name):
        raise HTTPException(status_code=409, detail="Ja existe um comodo com esse nome")
    return {"room": rooms_store.create_room(payload.name).to_dict()}


@app.patch("/rooms/{room_id}")
def patch_room(room_id: str, payload: RoomIn) -> dict[str, Any]:
    existente = rooms_store.find_by_name(payload.name)
    if existente and existente.id != room_id:
        raise HTTPException(status_code=409, detail="Ja existe um comodo com esse nome")
    room = rooms_store.update_room(room_id, name=payload.name)
    if not room:
        raise HTTPException(status_code=404, detail="Comodo nao encontrado")
    return {"room": room.to_dict()}


@app.delete("/rooms/{room_id}")
def remove_room(room_id: str) -> dict[str, Any]:
    if not rooms_store.delete_room(room_id):
        raise HTTPException(status_code=404, detail="Comodo nao encontrado")
    # Um comodo apagado nao pode continuar liberado no cadastro de ninguem.
    afetados = people_store.remove_room_everywhere(room_id)
    return {"deleted": True, "people_updated": afetados}


def _validar_comodos(ids: list[str]) -> None:
    for room_id in ids:
        if rooms_store.get_room(room_id) is None:
            raise HTTPException(
                status_code=400, detail=f"Comodo inexistente: {room_id}"
            )


def _parse_rooms(raw: str) -> list[str]:
    """Ids de comodos vindos do formulario multipart, separados por virgula.

    O cadastro sobe as amostras no mesmo POST, entao os comodos viajam como
    campo de formulario -- nao ha corpo JSON onde caber uma lista.
    """
    return [parte.strip() for parte in (raw or "").split(",") if parte.strip()]


@app.get("/categories")
def get_categories() -> dict[str, Any]:
    """Catalogo de categorias/vinculos oferecido na tela de cadastro."""
    return {"categories": list(people_store.CATEGORIES)}


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
    """Valida (1 rosto por amostra), grava e indexa as amostras.

    `index_sample` faz a validacao e a indexacao numa passada so: antes o
    servidor codificava a imagem para contar rostos e, no fim do cadastro,
    recodificava o banco inteiro com `load_known_faces()`. Retorna os avisos
    das amostras recusadas.
    """
    warnings: list[str] = []
    for upload, data in zip(files, datas):
        if not data:
            warnings.append(f"{upload.filename}: arquivo vazio")
            continue
        try:
            faces = face_engine.index_sample(person.id, data)
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
    category: str = Form(""),
    rooms: str = Form("", description="Ids dos comodos liberados, separados por virgula"),
    authorized: bool = Form(True),
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

    comodos = _parse_rooms(rooms)
    _validar_comodos(comodos)

    datas = [await f.read() for f in files]
    person = people_store.create_person(
        name=name, category=category, rooms=comodos, authorized=authorized
    )
    warnings = _store_samples(person, files, datas)
    stats = face_engine.stats()

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
    rooms: list[str] | None = None
    authorized: bool | None = None


@app.patch("/people/{person_id}")
def patch_person(person_id: str, patch: PersonPatch) -> dict[str, Any]:
    campos = patch.model_dump(exclude_unset=True)
    if campos.get("rooms") is not None:
        _validar_comodos(campos["rooms"])

    person = people_store.update_person(person_id, **campos)
    if not person:
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    return {"person": person.to_dict()}


@app.delete("/people/{person_id}")
def remove_person(person_id: str) -> dict[str, Any]:
    if not people_store.delete_person(person_id):
        raise HTTPException(status_code=404, detail="Pessoa nao encontrada")
    # So tira do indice quem foi removido -- reprocessar o banco inteiro aqui
    # fazia a exclusao demorar segundos por amostra cadastrada.
    stats = face_engine.forget_person(person_id)
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
        # Valida e ja indexa a captura numa passada so.
        faces = face_engine.index_sample(person.id, image)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if faces != 1:
        raise HTTPException(
            status_code=422,
            detail=f"A captura tem {faces} rosto(s); e necessario exatamente 1",
        )

    people_store.add_sample_bytes(person, image)
    updated = people_store.get_person(payload.person_id)
    return {"person": updated.to_dict() if updated else None}


class AssistantQuestion(BaseModel):
    question: str


class AssistantConfirm(BaseModel):
    proposal: dict[str, Any]


@app.post("/assistant/ask")
def assistant_ask(payload: AssistantQuestion) -> dict[str, Any]:
    """Pergunta em portugues -> consulta respondida ou proposta de mudanca."""
    if not payload.question.strip():
        raise HTTPException(status_code=400, detail="Pergunta vazia")
    return assistant.responder(payload.question)


@app.post("/assistant/apply")
def assistant_apply(payload: AssistantConfirm) -> dict[str, Any]:
    """Aplica uma proposta que o administrador confirmou na tela."""
    resultado = assistant.aplicar(payload.proposal)
    if not resultado.get("ok"):
        raise HTTPException(status_code=400, detail=resultado.get("answer", "Falhou"))
    return resultado


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
