"""Testes das rotas HTTP com o motor facial substituido por um duble.

Assim a suite roda sem dlib/face_recognition instalados (que sao pesados e
dependem de toolchain C++ no Windows).
"""
from __future__ import annotations

import pytest

pytest.importorskip("fastapi", reason="FastAPI nao instalado (ver requirements.txt)")

from fastapi.testclient import TestClient  # noqa: E402

import access_control  # noqa: E402
import detections_store  # noqa: E402
import face_engine  # noqa: E402
import rooms_store  # noqa: E402
import main  # noqa: E402
import people_store  # noqa: E402

KNOWN_JPEG = b"\xff\xd8\xff-rosto-conhecido"
UNKNOWN_JPEG = b"\xff\xd8\xff-rosto-desconhecido"
EMPTY_JPEG = b"\xff\xd8\xff-sem-rosto"
PRESENCA_JPEG = b"\xff\xd8\xff-alguem-sem-rosto-visivel"


@pytest.fixture
def client(monkeypatch):
    """Cliente HTTP com motor facial simulado e um cadastro conhecido."""
    person = people_store.create_person(
        "Joao Victor", category="Morador", authorized=True
    )

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
        if data == PRESENCA_JPEG:
            return "person_no_face", []
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
    # O indice em memoria tambem entra por duble: `index_sample` valida e
    # indexa a amostra numa passada so (ver face_engine).
    monkeypatch.setattr(face_engine, "index_sample", lambda person_id, data: 1 if data else 0)
    monkeypatch.setattr(face_engine, "stats", lambda: {"people": 1, "samples": 3})
    monkeypatch.setattr(
        face_engine, "forget_person", lambda person_id: {"people": 0, "samples": 0}
    )
    monkeypatch.setattr(face_engine, "load_known_faces", lambda: {"people": 1, "samples": 3})

    with TestClient(main.app) as c:
        c.person_id = person.id  # type: ignore[attr-defined]
        c.rotations = rotations  # type: ignore[attr-defined]
        yield c


def _post_detect(client, payload: bytes, source: str = "pir", room: str | None = None):
    data = {"source": source}
    if room is not None:
        data["room"] = room
    return client.post(
        "/detect",
        files={"file": ("frame.jpg", payload, "image/jpeg")},
        data=data,
    )


def test_health_reports_engine_state(client):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["engine_available"] is True
    assert "uptime_s" in body
    # Regras de acesso sao opcionais: sem OLLAMA_MODEL a integracao fica off.
    assert set(body["presence"]) == {"available", "error"}


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
    cozinha = rooms_store.create_room("Cozinha")
    resp = client.post(
        "/people",
        data={
            "name": "Maria Santos",
            "category": "Prestador",
            "rooms": cozinha.id,
            "authorized": "false",
        },
        files=[("files", ("a.jpg", b"amostra", "image/jpeg"))],
    )
    assert resp.status_code == 201
    person = resp.json()["person"]
    assert person["samples"] == 1
    assert person["status"] == "pendente"
    assert person["category"] == "Prestador"
    assert person["rooms"] == [cozinha.id]
    assert person["room_names"] == ["Cozinha"]

    listed = client.get("/people").json()["people"]
    assert any(p["id"] == person["id"] for p in listed)

    patched = client.patch(f"/people/{person['id']}", json={"authorized": True}).json()
    assert patched["person"]["authorized"] is True

    assert client.get(f"/people/{person['id']}/samples/1").status_code == 200
    assert client.get(f"/people/{person['id']}/samples/9").status_code == 404

    assert client.delete(f"/people/{person['id']}").json()["deleted"] is True
    assert client.delete(f"/people/{person['id']}").status_code == 404


def test_categorias_disponiveis_para_a_tela_de_cadastro(client):
    assert client.get("/categories").json()["categories"] == [
        "Morador",
        "Visitante",
        "Prestador",
    ]


def test_cadastro_com_comodo_inexistente_e_recusado(client):
    resp = client.post(
        "/people",
        data={"name": "Fantasma", "rooms": "nao-existe"},
        files=[("files", ("a.jpg", b"amostra", "image/jpeg"))],
    )
    assert resp.status_code == 400
    assert "inexistente" in resp.json()["detail"]


def test_edicao_troca_categoria_e_comodos(client):
    """Ponto do tasks.md: editar a pessoa ja cadastrada."""
    cozinha = rooms_store.create_room("Cozinha")
    garagem = rooms_store.create_room("Garagem")
    person_id = client.person_id  # type: ignore[attr-defined]

    body = client.patch(
        f"/people/{person_id}",
        json={"name": "Joao V. Alves", "category": "Prestador", "rooms": [garagem.id]},
    ).json()["person"]

    assert body["name"] == "Joao V. Alves"
    assert body["category"] == "Prestador"
    assert body["rooms"] == [garagem.id]
    assert body["access_label"] == "Prestador: Garagem"
    # A edicao nao mexe nas amostras nem na chave mestra.
    assert body["authorized"] is True

    # Desmarcar tudo tranca a pessoa em todo lugar, sem apagar o cadastro.
    vazio = client.patch(f"/people/{person_id}", json={"rooms": []}).json()["person"]
    assert vazio["rooms"] == []
    assert access_control.evaluate(
        people_store.get_person(person_id), rooms_store.get_room(cozinha.id)
    )[0] is False


def test_apagar_comodo_o_tira_do_cadastro_das_pessoas(client):
    cozinha = rooms_store.create_room("Cozinha")
    person_id = client.person_id  # type: ignore[attr-defined]
    people_store.update_person(person_id, rooms=[cozinha.id])

    body = client.delete(f"/rooms/{cozinha.id}").json()
    assert body["people_updated"] == 1
    assert people_store.get_person(person_id).rooms == []


def test_create_person_reports_invalid_samples(client, monkeypatch):
    monkeypatch.setattr(face_engine, "index_sample", lambda person_id, data: 2)
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
    monkeypatch.setattr(face_engine, "index_sample", lambda person_id, data: 0)
    person_id = client.person_id  # type: ignore[attr-defined]
    resp = client.post("/enroll/from-esp32", json={"person_id": person_id})
    assert resp.status_code == 422


def test_detection_files_land_in_isolated_dir(client):
    _post_detect(client, KNOWN_JPEG)
    assert list(detections_store.DETECTIONS_DIR.glob("*.jpg"))


# ==========================================================================
# Acesso por comodo
# ==========================================================================
def _restringir_a(client, *nomes_de_comodos):
    """Cria os comodos e deixa a pessoa liberada exatamente neles."""
    ids = [rooms_store.create_room(nome).id for nome in nomes_de_comodos]
    people_store.update_person(client.person_id, rooms=ids)
    return ids


def test_pessoa_autorizada_e_negada_no_comodo_errado(client):
    """Cenario do tasks.md: rosto reconhecido, mas fora dos comodos liberados."""
    _restringir_a(client, "Quarto do Joao", "Cozinha")
    rooms_store.create_room("Escritorio")

    body = _post_detect(client, KNOWN_JPEG, room="Escritorio").json()
    detection = body["detection"]

    assert body["status"] == "recognized"
    assert detection["person"] == "Joao Victor"
    assert detection["room"] == "Escritorio"
    assert detection["access_allowed"] is False
    # A lista de comodos restringe mesmo com a pessoa marcada como autorizada.
    assert detection["authorized"] is False
    assert "nao liberado" in detection["access_reason"]


def test_pessoa_autorizada_no_proprio_comodo(client):
    _restringir_a(client, "Quarto do Joao", "Cozinha")
    detection = _post_detect(client, KNOWN_JPEG, room="Cozinha").json()["detection"]
    assert detection["access_allowed"] is True
    assert detection["authorized"] is True


def test_todos_os_comodos_marcados_liberam_qualquer_lugar(client):
    """Caso "acessa tudo": todos os comodos marcados no cadastro."""
    _restringir_a(client, "Escritorio", "Cozinha", "Garagem")
    detection = _post_detect(client, KNOWN_JPEG, room="Escritorio").json()["detection"]
    assert detection["access_allowed"] is True
    assert detection["authorized"] is True


def test_sem_comodo_informado_vale_so_a_chave_mestra(client):
    """Compatibilidade com quem ainda nao cadastrou comodos."""
    _restringir_a(client, "Cozinha")
    detection = _post_detect(client, KNOWN_JPEG, room="").json()["detection"]
    assert detection["access_allowed"] is None
    assert detection["authorized"] is True


def test_chave_mestra_bloqueia_antes_dos_comodos(client):
    """`authorized` = false nega em qualquer comodo, mesmo marcado no cadastro."""
    _restringir_a(client, "Cozinha")
    people_store.update_person(client.person_id, authorized=False)

    detection = _post_detect(client, KNOWN_JPEG, room="Cozinha").json()["detection"]
    assert detection["access_allowed"] is False
    assert "sem autorizacao" in detection["access_reason"]


def test_detect_sem_room_usa_o_comodo_padrao_do_servidor(client, monkeypatch):
    """Fluxo do PIR: o firmware nao informa comodo, vale o ROOM do .env."""
    monkeypatch.setattr(main, "DEFAULT_ROOM", "Quarto do Joao")
    _restringir_a(client, "Quarto do Joao")

    detection = _post_detect(client, KNOWN_JPEG).json()["detection"]
    assert detection["room"] == "Quarto do Joao"
    assert detection["access_allowed"] is True


def test_scan_repassa_o_comodo_do_dispositivo(client, monkeypatch):
    monkeypatch.setattr(main.esp32_client, "capture", lambda url: KNOWN_JPEG)
    _restringir_a(client, "Cozinha")
    garagem = rooms_store.create_room("Garagem")

    detection = client.post(
        "/scan",
        json={"esp32_url": "http://192.168.15.72", "room_id": garagem.id},
    ).json()["detection"]
    assert detection["room"] == "Garagem"
    assert detection["access_allowed"] is False


def test_auto_unlock_nao_dispara_no_comodo_errado(client, monkeypatch):
    """A fechadura nao pode abrir para quem esta no lugar errado."""
    calls = []
    monkeypatch.setattr(main.esp32_client, "unlock", lambda url: calls.append(url) or True)
    monkeypatch.setattr(main, "AUTO_UNLOCK", True)
    _restringir_a(client, "Cozinha")
    rooms_store.create_room("Escritorio")

    assert _post_detect(client, KNOWN_JPEG, room="Escritorio").json()["unlocked"] is False
    assert calls == []


def test_spoof_nega_acesso_mesmo_com_rosto_reconhecido(client, monkeypatch):
    """G1: foto impressa de alguem autorizado nao pode abrir a fechadura."""
    monkeypatch.setattr(main, "LIVENESS_ENABLED", True)
    monkeypatch.setattr(
        main.liveness, "check", lambda rgb, box: {"vivo": False, "score": -2.1, "limiar": 0.0}
    )
    monkeypatch.setattr(main.face_engine, "decode_rgb", lambda data: None)
    calls: list[str] = []
    monkeypatch.setattr(main.esp32_client, "unlock", lambda url: calls.append(url) or True)
    monkeypatch.setattr(main, "AUTO_UNLOCK", True)

    body = _post_detect(client, KNOWN_JPEG).json()
    detection = body["detection"]

    assert body["status"] == "recognized"
    assert detection["person"] == "Joao Victor"
    assert detection["live"] is False
    assert detection["authorized"] is False
    assert body["unlocked"] is False
    assert calls == []


def test_vivacidade_desligada_nao_altera_o_fluxo(client):
    """Falha aberta: sem LIVENESS_ENABLED ninguem e barrado por vivacidade."""
    detection = _post_detect(client, KNOWN_JPEG).json()["detection"]
    assert detection["live"] is None
    assert detection["authorized"] is True
