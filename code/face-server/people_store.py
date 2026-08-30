"""Armazenamento simples (um JSON por pessoa) dos metadados de pessoas.

Estrutura em disco:
    people/<id>.json                -> metadados
    known_faces/<id>/<n>.jpg        -> amostras faciais
"""
from __future__ import annotations

import json
import re
import shutil
import time
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from config import KNOWN_FACES_DIR, MIN_SAMPLES_ACTIVE, PEOPLE_DIR

_SAMPLE_RE = re.compile(r"^(\d+)\.jpg$", re.IGNORECASE)


@dataclass
class Person:
    id: str
    name: str
    category: str = "Visitante"
    authorized: bool = False
    note: str = ""
    created_at: float = field(default_factory=time.time)

    def sample_dir(self) -> Path:
        return KNOWN_FACES_DIR / self.id

    def sample_files(self) -> list[Path]:
        d = self.sample_dir()
        if not d.is_dir():
            return []
        files = [p for p in d.iterdir() if _SAMPLE_RE.match(p.name)]
        return sorted(files, key=lambda p: int(p.stem))

    @property
    def samples(self) -> int:
        return len(self.sample_files())

    @property
    def status(self) -> str:
        return "ativo" if self.samples >= MIN_SAMPLES_ACTIVE else "pendente"

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["samples"] = self.samples
        data["status"] = self.status
        return data


def _meta_path(person_id: str) -> Path:
    return PEOPLE_DIR / f"{person_id}.json"


def _load(person_id: str) -> Person | None:
    path = _meta_path(person_id)
    if not path.is_file():
        return None
    raw = json.loads(path.read_text(encoding="utf-8"))
    return Person(
        id=raw["id"],
        name=raw.get("name", ""),
        category=raw.get("category", "Visitante"),
        authorized=bool(raw.get("authorized", False)),
        note=raw.get("note", ""),
        created_at=float(raw.get("created_at", time.time())),
    )


def _save(person: Person) -> None:
    PEOPLE_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "id": person.id,
        "name": person.name,
        "category": person.category,
        "authorized": person.authorized,
        "note": person.note,
        "created_at": person.created_at,
    }
    _meta_path(person.id).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def list_people() -> list[Person]:
    if not PEOPLE_DIR.is_dir():
        return []
    people = []
    for path in PEOPLE_DIR.glob("*.json"):
        p = _load(path.stem)
        if p:
            people.append(p)
    people.sort(key=lambda p: p.created_at, reverse=True)
    return people


def get_person(person_id: str) -> Person | None:
    return _load(person_id)


def create_person(
    name: str, category: str = "Visitante", authorized: bool = False, note: str = ""
) -> Person:
    person = Person(
        id=uuid.uuid4().hex[:12],
        name=name.strip(),
        category=category.strip() or "Visitante",
        authorized=authorized,
        note=note.strip(),
    )
    person.sample_dir().mkdir(parents=True, exist_ok=True)
    _save(person)
    return person


def update_person(person_id: str, **patch: Any) -> Person | None:
    person = _load(person_id)
    if not person:
        return None
    for key in ("name", "category", "note"):
        if key in patch and patch[key] is not None:
            setattr(person, key, str(patch[key]).strip())
    if patch.get("authorized") is not None:
        person.authorized = bool(patch["authorized"])
    _save(person)
    return person


def delete_person(person_id: str) -> bool:
    person = _load(person_id)
    if not person:
        return False
    shutil.rmtree(person.sample_dir(), ignore_errors=True)
    _meta_path(person_id).unlink(missing_ok=True)
    return True


def next_sample_index(person: Person) -> int:
    existing = [int(p.stem) for p in person.sample_files()]
    return (max(existing) + 1) if existing else 1


def add_sample_bytes(person: Person, data: bytes) -> Path:
    person.sample_dir().mkdir(parents=True, exist_ok=True)
    idx = next_sample_index(person)
    path = person.sample_dir() / f"{idx}.jpg"
    path.write_bytes(data)
    return path
