"""Motor de reconhecimento facial (OpenCV + face_recognition).

As dependencias pesadas (dlib / face_recognition, opencv) sao importadas de
forma tolerante: se faltarem, `ENGINE_AVAILABLE` fica False e as rotas que
dependem do motor respondem 503, enquanto o CRUD de pessoas e o /health
continuam funcionando.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import presence
from config import (
    KNOWN_FACES_DIR,
    MAX_ANALYSIS_WIDTH,
    PRESENCE_FALLBACK,
    TOLERANCE,
)

try:
    import cv2
    import numpy as np
    import face_recognition

    ENGINE_AVAILABLE = True
    ENGINE_ERROR = ""
except Exception as exc:  # pragma: no cover - depende do ambiente
    ENGINE_AVAILABLE = False
    ENGINE_ERROR = f"{type(exc).__name__}: {exc}"


_VALID_EXT = (".jpg", ".jpeg", ".png", ".webp")

# Estado em memoria: encodings alinhados com o person_id de origem.
_known_encodings: list = []
_known_person_ids: list[str] = []
_last_stats = {"people": 0, "samples": 0}


@dataclass
class FaceMatch:
    person_id: str | None
    name: str
    authorized: bool
    distance: float
    box: list[int]  # [top, right, bottom, left]


def stats() -> dict[str, int]:
    return dict(_last_stats)


def normalize_rotation(degrees: int) -> int:
    """Reduz o giro a 0/90/180/270; qualquer outro valor vira 0."""
    wrapped = int(degrees) % 360
    return wrapped if wrapped in (90, 180, 270) else 0


def rotate_jpeg(data: bytes, degrees: int) -> bytes:
    """Gira um JPEG e devolve outro JPEG.

    Usado para deixar o quadro na vertical ANTES do reconhecimento: o detector
    HOG do dlib nao encontra rosto de cabeca para baixo. A deteccao e gravada
    ja girada, entao a miniatura no app tambem sai na orientacao certa.
    """
    rotation = normalize_rotation(degrees)
    if rotation == 0:
        return data

    arr = np.frombuffer(data, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Imagem corrompida ou formato invalido")

    code = {
        90: cv2.ROTATE_90_CLOCKWISE,
        180: cv2.ROTATE_180,
        270: cv2.ROTATE_90_COUNTERCLOCKWISE,
    }[rotation]
    ok, encoded = cv2.imencode(".jpg", cv2.rotate(img, code))
    if not ok:
        raise ValueError("Falha ao recodificar a imagem girada")
    return encoded.tobytes()


def _decode_image(data: bytes):
    """Decodifica o JPEG em RGB, reduzindo o que for grande demais.

    O detector HOG do dlib custa proporcional a area: uma foto de celular
    (2544 px de largura) leva ~7,5 s, a mesma foto em 1024 px leva ~1,6 s e
    encontra o mesmo rosto. Os quadros do ESP32 (640x480) passam intactos.
    """
    arr = np.frombuffer(data, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Imagem corrompida ou formato invalido")
    largura = img.shape[1]
    if MAX_ANALYSIS_WIDTH and largura > MAX_ANALYSIS_WIDTH:
        altura = round(img.shape[0] * MAX_ANALYSIS_WIDTH / largura)
        img = cv2.resize(
            img, (MAX_ANALYSIS_WIDTH, altura), interpolation=cv2.INTER_AREA
        )
    return cv2.cvtColor(img, cv2.COLOR_BGR2RGB)


def _encodings_from_bytes(data: bytes):
    rgb = _decode_image(data)
    locations = face_recognition.face_locations(rgb)
    return face_recognition.face_encodings(rgb, locations), locations


def count_faces(data: bytes) -> int:
    """Numero de rostos numa imagem (usado na validacao de amostras)."""
    if not ENGINE_AVAILABLE:
        raise RuntimeError(ENGINE_ERROR)
    encs, _ = _encodings_from_bytes(data)
    return len(encs)


def _refresh_stats() -> dict[str, int]:
    _last_stats.update(
        people=len(set(_known_person_ids)), samples=len(_known_person_ids)
    )
    return dict(_last_stats)


def index_sample(person_id: str, data: bytes) -> int:
    """Indexa UMA amostra nova, sem reler o banco inteiro.

    Retorna quantos rostos a imagem tem; so indexa quando e exatamente 1, que
    e a mesma validacao exigida no cadastro.

    Existe por causa do tempo de resposta: cadastrar ou remover alguem chamava
    `load_known_faces()`, que recodifica TODAS as amostras de TODAS as pessoas
    (o dlib leva ~1 s por imagem). Com 5 pessoas de 4 amostras, salvar uma
    sexta pessoa custava 20 s de trabalho ja feito antes.
    """
    if not ENGINE_AVAILABLE:
        raise RuntimeError(ENGINE_ERROR)
    encs, _ = _encodings_from_bytes(data)
    if len(encs) != 1:
        return len(encs)
    _known_encodings.append(encs[0])
    _known_person_ids.append(person_id)
    _refresh_stats()
    return 1


def forget_person(person_id: str) -> dict[str, int]:
    """Tira do indice em memoria as amostras de uma pessoa removida."""
    global _known_encodings, _known_person_ids
    manter = [i for i, pid in enumerate(_known_person_ids) if pid != person_id]
    if len(manter) != len(_known_person_ids):
        _known_encodings = [_known_encodings[i] for i in manter]
        _known_person_ids = [_known_person_ids[i] for i in manter]
    return _refresh_stats()


def load_known_faces() -> dict[str, int]:
    """Le known_faces/<person_id>/*.jpg e regenera os encodings em memoria."""
    global _known_encodings, _known_person_ids
    _known_encodings = []
    _known_person_ids = []

    people = 0
    samples = 0
    if not ENGINE_AVAILABLE:
        _last_stats.update(people=0, samples=0)
        return dict(_last_stats)

    KNOWN_FACES_DIR.mkdir(parents=True, exist_ok=True)
    for person_dir in sorted(p for p in KNOWN_FACES_DIR.iterdir() if p.is_dir()):
        person_samples = 0
        for img_path in sorted(person_dir.iterdir()):
            if img_path.suffix.lower() not in _VALID_EXT:
                continue
            try:
                encs, _ = _encodings_from_bytes(img_path.read_bytes())
            except Exception as exc:
                print(f"  [WARN] {img_path}: {exc}")
                continue
            if not encs:
                print(f"  [WARN] nenhum rosto em {img_path}")
                continue
            _known_encodings.append(encs[0])
            _known_person_ids.append(person_dir.name)
            person_samples += 1
            samples += 1
        if person_samples:
            people += 1
            print(f"  [OK] {person_dir.name}: {person_samples} amostra(s)")

    _last_stats.update(people=people, samples=samples)
    print(f"[FACE] {people} rosto(s) / {samples} amostra(s) carregados")
    return dict(_last_stats)


def decode_rgb(data: bytes):
    """Quadro decodificado em RGB, para quem precisa analisar os pixels."""
    return _decode_image(data)


def identify(
    data: bytes,
    resolve: Callable[[str], tuple[str, bool]],
) -> tuple[str, list[FaceMatch]]:
    """Roda o pipeline de reconhecimento.

    `resolve(person_id) -> (name, authorized)` traduz o id para metadados.
    Retorna (status, matches) com status em
    {recognized, unknown, person_no_face, no_face}.

    `person_no_face` (G2) e o caso em que o reconhecimento nao extraiu nenhum
    rosto mas os detectores auxiliares acharam alguem no quadro -- rosto de
    lado, mal iluminado ou so o corpo. Antes isso virava `no_face` e sumia do
    log como se nao tivesse passado ninguem.
    """
    if not ENGINE_AVAILABLE:
        raise RuntimeError(ENGINE_ERROR)

    rgb = _decode_image(data)
    locations = face_recognition.face_locations(rgb)
    encs = face_recognition.face_encodings(rgb, locations)
    if not encs:
        if PRESENCE_FALLBACK:
            achado = presence.detect(rgb)
            if achado["presente"]:
                print(f"[PRESENCA] alguem no quadro via {achado['fonte']}")
                return "person_no_face", []
        return "no_face", []

    matches: list[FaceMatch] = []
    status = "unknown"
    for enc, box in zip(encs, locations):
        person_id: str | None = None
        name = "Desconhecido"
        authorized = False
        distance = 1.0
        if _known_encodings:
            distances = face_recognition.face_distance(_known_encodings, enc)
            best = int(np.argmin(distances))
            distance = float(distances[best])
            if distance <= TOLERANCE:
                person_id = _known_person_ids[best]
                name, authorized = resolve(person_id)
                status = "recognized"
        matches.append(
            FaceMatch(
                person_id=person_id,
                name=name,
                authorized=authorized,
                distance=round(distance, 4),
                box=[int(v) for v in box],
            )
        )
    return status, matches
