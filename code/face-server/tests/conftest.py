"""Fixtures compartilhadas: isolam o armazenamento em disco num tmp_path."""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

# Permite `import main`, `import people_store`, ... rodando de qualquer lugar.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import assistant  # noqa: E402
import detections_store  # noqa: E402
import people_store  # noqa: E402
import rooms_store  # noqa: E402


@pytest.fixture(autouse=True)
def isolated_storage(tmp_path, monkeypatch):
    """Redireciona known_faces/, people/, detections/ e rooms/ para tmp."""
    known = tmp_path / "known_faces"
    people = tmp_path / "people"
    detections = tmp_path / "detections"
    rooms = tmp_path / "rooms"
    # levels/ so e lida na migracao de cadastros antigos (ver people_store).
    levels = tmp_path / "levels"
    for d in (known, people, detections, rooms, levels):
        d.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(people_store, "KNOWN_FACES_DIR", known)
    monkeypatch.setattr(people_store, "PEOPLE_DIR", people)
    monkeypatch.setattr(people_store, "LEVELS_DIR", levels)
    monkeypatch.setattr(detections_store, "DETECTIONS_DIR", detections)
    monkeypatch.setattr(rooms_store, "ROOMS_DIR", rooms)
    # O assistente fica desligado por padrao: a suite nao pode depender do
    # .env da maquina nem de um Ollama no ar.
    monkeypatch.setattr(assistant, "OLLAMA_ENABLED", False)
    monkeypatch.setattr(assistant, "OLLAMA_MODEL", None)
    return tmp_path
