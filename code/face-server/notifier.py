"""Notificacao opcional via Telegram. Desligada sem token/chat id."""
from __future__ import annotations

from pathlib import Path

import requests

from config import (
    HTTP_TIMEOUT,
    TELEGRAM_BOT_TOKEN,
    TELEGRAM_CHAT_ID,
    TELEGRAM_ENABLED,
)


def send_photo(image_path: str | Path, caption: str) -> None:
    """Envia foto + legenda ao Telegram. No-op se o Telegram estiver desligado."""
    if not TELEGRAM_ENABLED:
        return
    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendPhoto"
    try:
        with open(image_path, "rb") as fh:
            files = {"photo": (Path(image_path).name, fh, "image/jpeg")}
            data = {"chat_id": TELEGRAM_CHAT_ID, "caption": caption}
            resp = requests.post(url, data=data, files=files, timeout=HTTP_TIMEOUT * 3)
        if not resp.ok:
            print(f"[TELEGRAM] erro {resp.status_code}: {resp.text}")
    except Exception as exc:  # pragma: no cover - rede
        print(f"[TELEGRAM] excecao: {exc}")
