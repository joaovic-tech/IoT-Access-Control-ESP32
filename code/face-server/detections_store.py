"""Historico de deteccoes em disco.

Cada deteccao gera dois arquivos em `detections/`:
    AAAAMMDD_HHMMSS_<status>_<nome>.jpg   -> imagem recebida
    AAAAMMDD_HHMMSS_<status>_<nome>.json  -> metadados (pessoa, categoria, origem)

O sidecar JSON evita ter que reconstruir a informacao a partir do nome do
arquivo e permite listar as deteccoes recentes sem reprocessar imagens.
"""
from __future__ import annotations

import json
import re
import time
import unicodedata
from datetime import datetime
from pathlib import Path
from typing import Any

from config import DETECTIONS_DIR

# Deteccoes mais antigas que este limite sao removidas a cada gravacao.
MAX_DETECTIONS = 500

_SLUG_RE = re.compile(r"[^a-zA-Z0-9]+")


def slugify(text: str, fallback: str = "sem-nome") -> str:
    """Normaliza um nome para uso seguro em nome de arquivo."""
    normalized = unicodedata.normalize("NFKD", text or "")
    ascii_only = normalized.encode("ascii", "ignore").decode("ascii")
    slug = _SLUG_RE.sub("-", ascii_only).strip("-").lower()
    return slug or fallback


def save(
    image: bytes,
    status: str,
    person: str = "",
    person_id: str | None = None,
    category: str = "",
    authorized: bool = False,
    distance: float | None = None,
    source: str = "unknown",
    unlocked: bool = False,
) -> dict[str, Any]:
    """Grava imagem + metadados e devolve o registro pronto para a API."""
    DETECTIONS_DIR.mkdir(parents=True, exist_ok=True)

    now = time.time()
    stamp = datetime.fromtimestamp(now).strftime("%Y%m%d_%H%M%S")
    base = f"{stamp}_{status}_{slugify(person, 'desconhecido')}"

    # Evita colisao quando duas deteccoes caem no mesmo segundo.
    candidate = base
    suffix = 1
    while (DETECTIONS_DIR / f"{candidate}.jpg").exists():
        suffix += 1
        candidate = f"{base}-{suffix}"

    image_path = DETECTIONS_DIR / f"{candidate}.jpg"
    image_path.write_bytes(image)

    record: dict[str, Any] = {
        "id": candidate,
        "file": image_path.name,
        "url": f"/media/detections/{image_path.name}",
        "time": now,
        "time_label": datetime.fromtimestamp(now).strftime("%d/%m/%Y %H:%M:%S"),
        "status": status,
        "person": person,
        "person_id": person_id,
        "category": category,
        "authorized": authorized,
        "distance": distance,
        "source": source,
        "unlocked": unlocked,
    }
    (DETECTIONS_DIR / f"{candidate}.json").write_text(
        json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    _prune()
    return record


def _read(meta_path: Path) -> dict[str, Any] | None:
    try:
        return json.loads(meta_path.read_text(encoding="utf-8"))
    except Exception:
        return None


def _all_oldest_first() -> list[tuple[Path, dict[str, Any]]]:
    """Sidecars validos, do mais antigo para o mais novo.

    A ordenacao usa o campo `time` do proprio registro, e nao o nome do
    arquivo: duas deteccoes no mesmo segundo compartilham o prefixo de data e
    o sufixo `_status_nome` inverteria a ordem cronologica.
    """
    if not DETECTIONS_DIR.is_dir():
        return []
    entries = []
    for meta_path in DETECTIONS_DIR.glob("*.json"):
        record = _read(meta_path)
        if record:
            entries.append((meta_path, record))
    entries.sort(key=lambda item: (item[1].get("time", 0.0), item[0].name))
    return entries


def list_recent(limit: int = 20, offset: int = 0) -> tuple[list[dict[str, Any]], int]:
    """Deteccoes mais recentes primeiro. Retorna (itens, total)."""
    entries = list(reversed(_all_oldest_first()))
    total = len(entries)
    return [record for _, record in entries[offset : offset + limit]], total


def _prune() -> None:
    """Mantem no maximo MAX_DETECTIONS registros (remove os mais antigos)."""
    entries = _all_oldest_first()
    excess = len(entries) - MAX_DETECTIONS
    for meta_path, _ in entries[:excess] if excess > 0 else []:
        meta_path.with_suffix(".jpg").unlink(missing_ok=True)
        meta_path.unlink(missing_ok=True)
