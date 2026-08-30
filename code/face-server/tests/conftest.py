"""Fixtures compartilhadas: isolam o armazenamento em disco num tmp_path."""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

# Permite `import main`, `import people_store`, ... rodando de qualquer lugar.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import detections_store  # noqa: E402
import people_store  # noqa: E402


@pytest.fixture(autouse=True)
def isolated_storage(tmp_path, monkeypatch):
    """Redireciona known_faces/, people/ e detections/ para um diretorio temporario."""
    known = tmp_path / "known_faces"
    people = tmp_path / "people"
    detections = tmp_path / "detections"
    for d in (known, people, detections):
        d.mkdir(parents=True, exist_ok=True)

    monkeypatch.setattr(people_store, "KNOWN_FACES_DIR", known)
    monkeypatch.setattr(people_store, "PEOPLE_DIR", people)
    monkeypatch.setattr(detections_store, "DETECTIONS_DIR", detections)
    return tmp_path
