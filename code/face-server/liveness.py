"""G1 -- deteccao de vivacidade (anti-spoofing) em quadro unico.

Ataque que isto barra: mostrar uma **foto impressa ou a tela de um celular** com
o rosto de alguem autorizado para a camera. Sem esta checagem, o reconhecimento
facial aceita a foto exatamente como aceitaria a pessoa -- e a fechadura abre.

Por que quadro unico
--------------------
O firmware envia **um JPEG** por evento do PIR. Metodos baseados em movimento
(piscar de olhos, micro-expressao) precisariam de video e de mudar o
`face_client.cpp`. Aqui a decisao sai de um so quadro, usando pistas de textura
e frequencia -- a linha de "colour texture analysis" da literatura de
anti-spoofing.

Pistas extraidas do recorte do rosto (`features`)
-------------------------------------------------
1. nitidez (variancia do laplaciano) -- reimpressao perde microtextura
2. energia de alta frequencia (FFT)  -- papel e tela cortam altas frequencias
3. pico de moire (FFT)               -- tela fotografada gera padrao periodico
4. entropia e desvio do LBP          -- textura da pele x textura do papel
5. saturacao media e desvio          -- impressao/tela deslocam a cor
6. reflexo especular                 -- brilho da tela satura pixels

Calibracao
----------
O modulo **nao decide nada sem um modelo calibrado**. Colete amostras reais e
falsas e rode `tools/liveness_calibrate.py`, que ajusta um discriminante linear
(Fisher/LDA) e grava `liveness_model.json`. Sem esse arquivo a verificacao fica
desligada e o pipeline se comporta como antes -- **falha aberta de proposito**:
um sistema de acesso nao pode trancar todo mundo do lado de fora porque faltou
um arquivo de calibracao.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

try:
    import cv2
    import numpy as np

    LIVENESS_DEPS = True
    LIVENESS_ERROR = ""
except Exception as exc:  # pragma: no cover - depende do ambiente
    LIVENESS_DEPS = False
    LIVENESS_ERROR = f"{type(exc).__name__}: {exc}"

MODEL_PATH = Path(__file__).resolve().parent / "liveness_model.json"

FEATURE_NAMES = (
    "nitidez",
    "alta_frequencia",
    "moire",
    "lbp_entropia",
    "lbp_desvio",
    "saturacao_media",
    "saturacao_desvio",
    "especular",
)

# Preenchido por load_model(): {"w": [...], "b": float, "limiar": float, ...}
_model: dict[str, Any] | None = None


def load_model() -> dict[str, Any] | None:
    """Le liveness_model.json, se existir. Chamado na subida do servidor."""
    global _model
    if not MODEL_PATH.is_file():
        _model = None
        return None
    try:
        dados = json.loads(MODEL_PATH.read_text(encoding="utf-8"))
        if not isinstance(dados.get("w"), list) or len(dados["w"]) != len(FEATURE_NAMES):
            raise ValueError("vetor de pesos com tamanho inesperado")
        _model = dados
        print(
            f"[VIVACIDADE] modelo carregado (limiar {dados.get('limiar', 0.0):.3f}, "
            f"acuracia informada {dados.get('acuracia', 0.0):.1%})"
        )
    except Exception as exc:
        print(f"[VIVACIDADE] modelo invalido, verificacao desligada: {exc}")
        _model = None
    return _model


def enabled() -> bool:
    """So decide quando ha dependencias E um modelo calibrado."""
    return LIVENESS_DEPS and _model is not None


def _recorte(rgb: Any, box: list[int] | None) -> Any:
    """Recorta o rosto com uma folga; sem caixa, usa o quadro inteiro."""
    if box is None:
        return rgb
    top, right, bottom, left = box
    altura, largura = rgb.shape[:2]
    folga = int(0.15 * max(bottom - top, right - left))
    top = max(0, top - folga)
    left = max(0, left - folga)
    bottom = min(altura, bottom + folga)
    right = min(largura, right + folga)
    if bottom - top < 20 or right - left < 20:
        return rgb
    return rgb[top:bottom, left:right]


def _lbp(gray: Any) -> Any:
    """LBP 3x3 sem dependencia externa (skimage nao esta no requirements)."""
    centro = gray[1:-1, 1:-1]
    codigo = np.zeros_like(centro, dtype=np.uint8)
    vizinhos = (
        (-1, -1), (-1, 0), (-1, 1),
        (0, 1), (1, 1), (1, 0),
        (1, -1), (0, -1),
    )
    for bit, (dy, dx) in enumerate(vizinhos):
        deslocado = gray[1 + dy : gray.shape[0] - 1 + dy, 1 + dx : gray.shape[1] - 1 + dx]
        codigo |= ((deslocado >= centro) << bit).astype(np.uint8)
    return codigo


def features(rgb: Any, box: list[int] | None = None) -> Any:
    """Vetor de caracteristicas do rosto, na ordem de FEATURE_NAMES."""
    if not LIVENESS_DEPS:
        raise RuntimeError(LIVENESS_ERROR)

    face = _recorte(rgb, box)
    face = cv2.resize(face, (160, 160), interpolation=cv2.INTER_AREA)
    gray = cv2.cvtColor(face, cv2.COLOR_RGB2GRAY)
    hsv = cv2.cvtColor(face, cv2.COLOR_RGB2HSV)

    nitidez = float(cv2.Laplacian(gray, cv2.CV_64F).var())

    # Espectro centralizado: a energia longe do centro sao as altas frequencias.
    espectro = np.abs(np.fft.fftshift(np.fft.fft2(gray.astype(np.float64))))
    total = float(espectro.sum()) + 1e-6
    cy, cx = espectro.shape[0] // 2, espectro.shape[1] // 2
    baixas = espectro[cy - 20 : cy + 20, cx - 20 : cx + 20].sum()
    alta_frequencia = float((total - baixas) / total)

    # Moire: picos isolados fora do centro denunciam padrao periodico de tela.
    anel = espectro.copy()
    anel[cy - 20 : cy + 20, cx - 20 : cx + 20] = 0
    moire = float(anel.max() / (anel.mean() + 1e-6))

    codigo = _lbp(gray)
    hist = np.bincount(codigo.ravel(), minlength=256).astype(np.float64)
    hist /= hist.sum() + 1e-6
    lbp_entropia = float(-(hist[hist > 0] * np.log2(hist[hist > 0])).sum())
    lbp_desvio = float(hist.std())

    saturacao = hsv[:, :, 1].astype(np.float64)
    saturacao_media = float(saturacao.mean())
    saturacao_desvio = float(saturacao.std())

    valor = hsv[:, :, 2]
    especular = float((valor > 240).sum() / valor.size)

    return np.array(
        [
            nitidez,
            alta_frequencia,
            moire,
            lbp_entropia,
            lbp_desvio,
            saturacao_media,
            saturacao_desvio,
            especular,
        ],
        dtype=np.float64,
    )


def score(rgb: Any, box: list[int] | None = None) -> float | None:
    """Pontuacao do discriminante. Maior = mais provavel ser rosto real."""
    if not enabled():
        return None
    try:
        x = features(rgb, box)
        media = np.array(_model["media"], dtype=np.float64)
        escala = np.array(_model["escala"], dtype=np.float64)
        w = np.array(_model["w"], dtype=np.float64)
        z = (x - media) / np.where(escala == 0, 1.0, escala)
        return float(np.dot(w, z) + _model["b"])
    except Exception as exc:  # pragma: no cover - robustez
        print(f"[VIVACIDADE] falha ao pontuar o quadro: {exc}")
        return None


def check(rgb: Any, box: list[int] | None = None) -> dict[str, Any]:
    """Verdito de vivacidade.

    `vivo` e None quando a verificacao esta desligada -- o chamador segue o
    comportamento antigo, sem bloquear ninguem.
    """
    pontuacao = score(rgb, box)
    if pontuacao is None:
        return {"vivo": None, "score": None, "limiar": None}
    limiar = float(_model.get("limiar", 0.0))
    return {"vivo": pontuacao >= limiar, "score": round(pontuacao, 4), "limiar": limiar}


def stats() -> dict[str, Any]:
    """Estado da verificacao, exposto em /health."""
    return {
        "enabled": enabled(),
        "model_file": MODEL_PATH.name if _model else "",
        "accuracy": (_model or {}).get("acuracia"),
        "error": LIVENESS_ERROR,
    }
