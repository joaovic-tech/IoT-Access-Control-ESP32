"""Chamadas HTTP do servidor para o ESP32-CAM (capturar frame, destravar)."""
from __future__ import annotations

import requests

from config import ESP32_ACCESS_TOKEN, HTTP_TIMEOUT


def _with_key(params: dict | None = None) -> dict:
    params = dict(params or {})
    if ESP32_ACCESS_TOKEN:
        params["key"] = ESP32_ACCESS_TOKEN
    return params


def capture(esp32_url: str) -> bytes:
    """GET {esp32_url}/capture -> bytes JPEG."""
    url = esp32_url.rstrip("/") + "/capture"
    resp = requests.get(url, timeout=HTTP_TIMEOUT)
    resp.raise_for_status()
    return resp.content


def unlock(esp32_url: str) -> bool:
    """GET {esp32_url}/api/unlock. Retorna True em caso de sucesso."""
    url = esp32_url.rstrip("/") + "/api/unlock"
    try:
        resp = requests.get(url, params=_with_key(), timeout=HTTP_TIMEOUT)
        return resp.ok
    except requests.RequestException as exc:
        print(f"[ESP32] falha ao destravar {url}: {exc}")
        return False
