"""F1 -- cadastro de comodos (um JSON por comodo).

Estrutura em disco:
    rooms/<id>.json    {id, name, slug, created_at}

O `slug` e o que permite casar o comodo informado pelo dispositivo (texto livre,
com acento e caixa variando) com o cadastro: "Quarto do Joao", "quarto do joão"
e "QUARTO DO JOÃO" caem todos no mesmo `quarto-do-joao`.
"""
from __future__ import annotations

import json
import time
import uuid
from dataclasses import asdict, dataclass, field
from typing import Any

from config import ROOMS_DIR
from detections_store import slugify


@dataclass
class Room:
    id: str
    name: str
    created_at: float = field(default_factory=time.time)

    @property
    def slug(self) -> str:
        return slugify(self.name, self.id)

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["slug"] = self.slug
        return data


def _path(room_id: str):
    return ROOMS_DIR / f"{room_id}.json"


def _load(room_id: str) -> Room | None:
    caminho = _path(room_id)
    if not caminho.is_file():
        return None
    try:
        raw = json.loads(caminho.read_text(encoding="utf-8"))
    except Exception:
        return None
    return Room(
        id=raw["id"],
        name=raw.get("name", ""),
        created_at=float(raw.get("created_at", time.time())),
    )


def _save(room: Room) -> None:
    ROOMS_DIR.mkdir(parents=True, exist_ok=True)
    _path(room.id).write_text(
        json.dumps(
            {"id": room.id, "name": room.name, "created_at": room.created_at},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def list_rooms() -> list[Room]:
    if not ROOMS_DIR.is_dir():
        return []
    comodos = [r for r in (_load(p.stem) for p in ROOMS_DIR.glob("*.json")) if r]
    comodos.sort(key=lambda r: r.name.lower())
    return comodos


def get_room(room_id: str) -> Room | None:
    return _load(room_id)


def find_by_name(name: str) -> Room | None:
    """Casa um comodo pelo nome, ignorando acento e caixa (F5)."""
    alvo = slugify(name, "")
    if not alvo:
        return None
    for room in list_rooms():
        if room.slug == alvo:
            return room
    return None


def resolve(room_id: str | None = None, name: str | None = None) -> Room | None:
    """Aceita id ou nome -- o app manda id, o firmware manda o texto do .env."""
    if room_id:
        room = _load(room_id)
        if room:
            return room
    if name:
        return find_by_name(name)
    return None


def create_room(name: str) -> Room:
    room = Room(id=uuid.uuid4().hex[:12], name=name.strip())
    _save(room)
    return room


def update_room(room_id: str, name: str | None = None) -> Room | None:
    room = _load(room_id)
    if not room:
        return None
    if name is not None:
        room.name = name.strip()
    _save(room)
    return room


def delete_room(room_id: str) -> bool:
    caminho = _path(room_id)
    if not caminho.is_file():
        return False
    caminho.unlink()
    return True
