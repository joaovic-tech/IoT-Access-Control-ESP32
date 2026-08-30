"""Motor de reconhecimento facial (OpenCV + face_recognition).

As dependencias pesadas (dlib / face_recognition, opencv) sao importadas de
forma tolerante: se faltarem, `ENGINE_AVAILABLE` fica False e as rotas que
dependem do motor respondem 503, enquanto o CRUD de pessoas e o /health
continuam funcionando.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from config import KNOWN_FACES_DIR, TOLERANCE

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
    arr = np.frombuffer(data, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Imagem corrompida ou formato invalido")
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


def identify(
    data: bytes,
    resolve: Callable[[str], tuple[str, bool]],
) -> tuple[str, list[FaceMatch]]:
    """Roda o pipeline de reconhecimento.

    `resolve(person_id) -> (name, authorized)` traduz o id para metadados.
    Retorna (status, matches) com status em {recognized, unknown, no_face}.
    """
    if not ENGINE_AVAILABLE:
        raise RuntimeError(ENGINE_ERROR)

    encs, locations = _encodings_from_bytes(data)
    if not encs:
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
