"""G2 -- segunda opiniao sobre a presenca de alguem no quadro.

Problema que resolve: o detector HOG do dlib e exigente (rosto frontal, nitido,
razoavelmente grande). Quando ele nao acha nada, o sistema registrava `no_face`
-- "quadro vazio" -- mesmo com uma pessoa visivel, de lado ou de costas. Para um
log de seguranca isso apaga justamente o evento mais interessante: alguem
passou e nao foi identificado.

Aqui roda um segundo conjunto de detectores, mais tolerantes, so quando o dlib
nao encontrou rosto:

    1. Haar frontal  -- rosto de frente que o HOG perdeu (pouca luz, angulo)
    2. Haar de perfil -- rosto de lado (nos dois sentidos, com espelhamento)
    3. HOG de pessoas -- corpo inteiro, quando nem o rosto aparece

Todos ja acompanham o `opencv-python-headless` que o projeto instala: nenhum
download, nenhuma dependencia nova, nada sai da maquina.
"""
from __future__ import annotations

import os
from typing import Any

try:
    import cv2
    import numpy as np

    _CASCADE_DIR = cv2.data.haarcascades
    _frontal = cv2.CascadeClassifier(
        os.path.join(_CASCADE_DIR, "haarcascade_frontalface_default.xml")
    )
    _profile = cv2.CascadeClassifier(
        os.path.join(_CASCADE_DIR, "haarcascade_profileface.xml")
    )
    _hog = cv2.HOGDescriptor()
    _hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

    PRESENCE_AVAILABLE = not (_frontal.empty() or _profile.empty())
    PRESENCE_ERROR = "" if PRESENCE_AVAILABLE else "cascatas Haar nao carregaram"
except Exception as exc:  # pragma: no cover - depende do ambiente
    PRESENCE_AVAILABLE = False
    PRESENCE_ERROR = f"{type(exc).__name__}: {exc}"

# Vazio = nada encontrado. Os demais valores identificam qual detector achou.
FONTE_FRONTAL = "haar-frontal"
FONTE_PERFIL = "haar-perfil"
FONTE_CORPO = "hog-pessoa"


def _para_cinza(image: Any):
    gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
    # Equaliza o histograma: o ESP32-CAM entrega quadros bem escuros a noite,
    # e as cascatas Haar sao sensiveis a contraste.
    return cv2.equalizeHist(gray)


def detect(rgb: Any) -> dict[str, Any]:
    """Procura rosto ou corpo num quadro RGB ja decodificado.

    Retorna `{"presente": bool, "fonte": str, "box": [t, r, b, l] | None}`.
    Chamado apenas quando o reconhecimento facial nao achou nada, entao o custo
    extra nao entra no caminho normal de uma deteccao bem-sucedida.
    """
    vazio: dict[str, Any] = {"presente": False, "fonte": "", "box": None}
    if not PRESENCE_AVAILABLE or rgb is None:
        return vazio

    try:
        gray = _para_cinza(rgb)

        faces = _frontal.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40))
        if len(faces):
            return _resultado(faces[0], FONTE_FRONTAL)

        perfis = _profile.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40))
        if len(perfis):
            return _resultado(perfis[0], FONTE_PERFIL)

        # A cascata de perfil so reconhece um dos lados; espelhar cobre o outro.
        espelhado = cv2.flip(gray, 1)
        perfis = _profile.detectMultiScale(espelhado, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40))
        if len(perfis):
            x, y, w, h = perfis[0]
            x = espelhado.shape[1] - x - w  # volta a coordenada para a imagem original
            return _resultado((x, y, w, h), FONTE_PERFIL)

        # Ultimo recurso: corpo inteiro. Mais lento, por isso fica no fim.
        corpos, _ = _hog.detectMultiScale(gray, winStride=(8, 8), padding=(8, 8), scale=1.05)
        if len(corpos):
            return _resultado(corpos[0], FONTE_CORPO)
    except Exception as exc:  # pragma: no cover - robustez em quadro corrompido
        print(f"[PRESENCA] falha ao analisar o quadro: {exc}")
        return vazio

    return vazio


def _resultado(box: Any, fonte: str) -> dict[str, Any]:
    x, y, w, h = (int(v) for v in box)
    # Mesma convencao de caixa do face_recognition: [topo, direita, base, esquerda].
    return {"presente": True, "fonte": fonte, "box": [y, x + w, y + h, x]}


def stats() -> dict[str, Any]:
    """Estado da deteccao de presenca, exposto em /health."""
    return {"available": PRESENCE_AVAILABLE, "error": PRESENCE_ERROR}
