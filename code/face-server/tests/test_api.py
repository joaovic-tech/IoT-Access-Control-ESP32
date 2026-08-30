"""Testes das rotas HTTP com o motor facial substituido por um duble.

Assim a suite roda sem dlib/face_recognition instalados (que sao pesados e
dependem de toolchain C++ no Windows).
"""
from __future__ import annotations

import pytest

pytest.importorskip("fastapi", reason="FastAPI nao instalado (ver requirements.txt)")

from fastapi.testclient import TestClient  # noqa: E402

import detections_store  # noqa: E402
import face_engine  # noqa: E402
import main  # noqa: E402
import people_store  # noqa: E402

KNOWN_JPEG = b"\xff\xd8\xff-rosto-conhecido"
UNKNOWN_JPEG = b"\xff\xd8\xff-rosto-desconhecido"
EMPTY_JPEG = b"\xff\xd8\xff-sem-rosto"


@pytest.fixture
def client(monkeypatch):
    """Cliente HTTP com motor facial simulado e um cadastro conhecido."""
    person = people_store.create_person("Joao Victor", "Morador", authorized=True)

    def fake_identify(data, resolve):
        if data == KNOWN_JPEG:
            name, authorized = resolve(person.id)
            match = face_engine.FaceMatch(
                person_id=person.id,
                name=name,
                authorized=authorized,
                distance=0.31,
                box=[10, 90, 90, 10],
            )
            return "recognized", [match]
        if data == UNKNOWN_JPEG:
            match = face_engine.FaceMatch(
                person_id=None,
                name="Desconhecido",
                authorized=False,
                distance=0.82,
                box=[5, 80, 80, 5],
            )
            return "unknown", [match]
        return "no_face", []

    # Registra o giro pedido sem depender do OpenCV (os payloads sao falsos).
    rotations: list[int] = []

    def fake_rotate(data: bytes, degrees: int) -> bytes:
        rotations.append(face_engine.normalize_rotation(degrees))
        return data

    monkeypatch.setattr(face_engine, "ENGINE_AVAILABLE", True)
    monkeypatch.setattr(face_engine, "ENGINE_ERROR", "")
    monkeypatch.setattr(face_engine, "identify", fake_identify)
    monkeypatch.setattr(face_engine, "rotate_jpeg", fake_rotate)
    monkeypatch.setattr(face_engine, "count_faces", lambda data: 1 if data else 0)
    monkeypatch.setattr(face_engine, "load_known_faces", lambda: {"people": 1, "samples": 3})

    with TestClient(main.app) as c:
        c.person_id = person.id  # type: ignore[attr-defined]
        c.rotations = rotations  # type: ignore[attr-defined]
        yield c


def _post_detect(client, payload: bytes, source: str = "pir"):
    return client.post(
        "/detect",
        files={"file": ("frame.jpg", payload, "image/jpeg")},
        data={"source": source},
    )


def test_health_reports_engine_state(client):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["engine_available"] is True
    assert "uptime_s" in body


def test_detect_recognized_face(client):
    body = _post_detect(client, KNOWN_JPEG).json()
    assert body["status"] == "recognized"
    assert body["detected"][0]["name"] == "Joao Victor"
    assert body["detected"][0]["authorized"] is True
    assert body["detection"]["category"] == "Morador"
    assert body["detection"]["source"] == "pir"
    assert body["file"].endswith("_recognized_joao-victor.jpg")


def test_detect_unknown_face(client):
    body = _post_detect(client, UNKNOWN_JPEG).json()
    assert body["status"] == "unknown"
    assert body["detected"][0]["person_id"] is None
    assert body["detection"]["authorized"] is False


def test_detect_without_face(client):
    body = _post_detect(client, EMPTY_JPEG).json()
    assert body["status"] == "no_face"
    assert body["detected"] == []


def test_detect_rejects_empty_upload(client):
    resp = client.post("/detect", files={"file": ("frame.jpg", b"", "image/jpeg")})
    assert resp.status_code == 400


def test_detections_listing_is_newest_first(client):
    _post_detect(client, UNKNOWN_JPEG)
    _post_detect(client, KNOWN_JPEG)
    body = client.get("/detections", params={"limit": 5}).json()
    assert body["total"] == 2
    assert body["items"][0]["status"] == "recognized"


def test_detect_returns_503_without_engine(client, monkeypatch):
    monkeypatch.setattr(face_engine, "ENGINE_AVAILABLE", False)
    monkeypatch.setattr(face_engine, "ENGINE_ERROR", "dlib ausente")
    assert _post_detect(client, KNOWN_JPEG).status_code == 503


def test_auto_unlock_calls_esp32_only_when_enabled(client, monkeypatch):
    calls: list[str] = []
    monkeypatch.setattr(main.esp32_client, "unlock", lambda url: calls.append(url) or True)

    monkeypatch.setattr(main, "AUTO_UNLOCK", False)
    assert _post_detect(client, KNOWN_JPEG).json()["unlocked"] is False
    assert calls == []

    monkeypatch.setattr(main, "AUTO_UNLOCK", True)
    assert _post_detect(client, KNOWN_JPEG).json()["unlocked"] is True
    assert len(calls) == 1


def test_people_crud_roundtrip(client):
    resp = client.post(
        "/people",
        data={"name": "Maria Santos", "category": "Visitante", "authorized": "false"},
        files=[("files", ("a.jpg", b"amostra", "image/jpeg"))],
    )
    assert resp.status_code == 201
    person = resp.json()["person"]
    assert person["samples"] == 1
    assert person["status"] == "pendente"

    listed = client.get("/people").json()["people"]
    assert any(p["id"] == person["id"] for p in listed)

    patched = client.patch(f"/people/{person['id']}", json={"authorized": True}).json()
    assert patched["person"]["authorized"] is True

    assert client.get(f"/people/{person['id']}/samples/1").status_code == 200
    assert client.get(f"/people/{person['id']}/samples/9").status_code == 404

    assert client.delete(f"/people/{person['id']}").json()["deleted"] is True
    assert client.delete(f"/people/{person['id']}").status_code == 404


def test_create_person_reports_invalid_samples(client, monkeypatch):
    monkeypatch.setattr(face_engine, "count_faces", lambda data: 2)
    body = client.post(
        "/people",
        data={"name": "Duplicado"},
        files=[("files", ("a.jpg", b"amostra", "image/jpeg"))],
    ).json()
    assert body["person"]["samples"] == 0
    assert "2 rosto(s)" in body["warnings"][0]


def test_scan_pulls_frame_from_esp32(client, monkeypatch):
    monkeypatch.setattr(main.esp32_client, "capture", lambda url: KNOWN_JPEG)
    body = client.post("/scan", json={"esp32_url": "http://192.168.15.72"}).json()
    assert body["status"] == "recognized"
    assert body["detection"]["source"] == "scan"


def test_scan_repassa_a_orientacao_do_app(client, monkeypatch):
    """Camera invertida: o quadro precisa ser girado ANTES do reconhecimento."""
    monkeypatch.setattr(main.esp32_client, "capture", lambda url: KNOWN_JPEG)
    client.rotations.clear()
    client.post("/scan", json={"esp32_url": "http://192.168.15.72", "rotation": 180})
    assert client.rotations == [180]


def test_detect_sem_rotation_usa_o_padrao_do_servidor(client, monkeypatch):
    """Fluxo do PIR: o firmware nao informa orientacao, vale o ROTATION do .env."""
    monkeypatch.setattr(main, "DEFAULT_ROTATION", 180)
    client.rotations.clear()
    _post_detect(client, KNOWN_JPEG)
    assert client.rotations == [180]

    # Um valor explicito na requisicao tem prioridade sobre o padrao.
    client.rotations.clear()
    client.post(
        "/detect",
        files={"file": ("frame.jpg", KNOWN_JPEG, "image/jpeg")},
        data={"source": "pir", "rotation": "90"},
    )
    assert client.rotations == [90]


def test_scan_reports_offline_device(client, monkeypatch):
    import requests

    def boom(url):
        raise requests.ConnectionError("host inacessivel")

    monkeypatch.setattr(main.esp32_client, "capture", boom)
    resp = client.post("/scan", json={"esp32_url": "http://192.168.15.99"})
    assert resp.status_code == 502


def test_enroll_from_esp32_adds_sample(client, monkeypatch):
    monkeypatch.setattr(main.esp32_client, "capture", lambda url: KNOWN_JPEG)
    person_id = client.person_id  # type: ignore[attr-defined]
    body = client.post("/enroll/from-esp32", json={"person_id": person_id}).json()
    assert body["person"]["samples"] == 1


def test_enroll_rejects_capture_without_single_face(client, monkeypatch):
    monkeypatch.setattr(main.esp32_client, "capture", lambda url: EMPTY_JPEG)
    monkeypatch.setattr(face_engine, "count_faces", lambda data: 0)
    person_id = client.person_id  # type: ignore[attr-defined]
    resp = client.post("/enroll/from-esp32", json={"person_id": person_id})
    assert resp.status_code == 422


def test_detection_files_land_in_isolated_dir(client):
    _post_detect(client, KNOWN_JPEG)
    assert list(detections_store.DETECTIONS_DIR.glob("*.jpg"))
