"""G1 -- calibra o detector de vivacidade a partir das suas amostras.

Material de TCC + ferramenta de operacao. Nenhum modulo do servidor importa
este arquivo; ele apenas PRODUZ o `liveness_model.json` que o `liveness.py` le.

Como coletar as amostras
------------------------
Monte duas pastas e fotografe **com a mesma camera do sistema** (o ESP32-CAM),
na mesma posicao e iluminacao do uso real -- calibrar com fotos de celular e
depois rodar com o ESP32 nao funciona, as texturas mudam:

    datasets/liveness/real/    rostos de pessoas de verdade na frente da camera
    datasets/liveness/spoof/   a camera apontada para uma FOTO IMPRESSA ou para
                               a TELA de um celular/monitor mostrando um rosto

Recomendado: 20+ imagens de cada, variando pessoa, angulo e iluminacao.

Uso
---
    .venv\\Scripts\\python.exe tools/liveness_calibrate.py
    .venv\\Scripts\\python.exe tools/liveness_calibrate.py --dataset outra/pasta

O que faz
---------
Extrai as caracteristicas de cada imagem, normaliza, ajusta um **discriminante
linear de Fisher (LDA)** -- sem sklearn, so numpy -- escolhe o limiar que
maximiza a acuracia e grava o modelo. Reporta acuracia por validacao
leave-one-out, que e honesta com poucas amostras.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import face_engine  # noqa: E402
import liveness  # noqa: E402

VALID_EXT = (".jpg", ".jpeg", ".png", ".webp")


def extrair(pasta: Path) -> np.ndarray:
    """Vetores de caracteristicas de todas as imagens de uma pasta."""
    if not pasta.is_dir():
        raise SystemExit(f"Pasta nao encontrada: {pasta}")

    vetores = []
    for img in sorted(pasta.iterdir()):
        if img.suffix.lower() not in VALID_EXT:
            continue
        try:
            rgb = face_engine._decode_image(img.read_bytes())
        except Exception as exc:
            print(f"  [WARN] {img.name}: {exc}")
            continue
        # Enquadra o rosto quando possivel; senao usa o quadro inteiro.
        caixas = face_engine.face_recognition.face_locations(rgb)
        box = list(caixas[0]) if caixas else None
        try:
            vetores.append(liveness.features(rgb, box))
        except Exception as exc:
            print(f"  [WARN] {img.name}: {exc}")
    print(f"  {pasta.name}: {len(vetores)} imagem(ns) utilizavel(is)")
    return np.array(vetores, dtype=np.float64)


def ajustar_lda(X: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, float, float]:
    """Discriminante de Fisher + limiar de melhor acuracia.

    Retorna (w, b, limiar). Classe 1 = rosto real, classe 0 = falso.
    """
    reais = X[y == 1]
    falsos = X[y == 0]
    mu1, mu0 = reais.mean(axis=0), falsos.mean(axis=0)

    # Covariancia intra-classe agrupada, com regularizacao (poucas amostras
    # tornam a matriz mal condicionada ou singular).
    n = len(X)
    Sw = np.cov(reais, rowvar=False) * (len(reais) - 1) + np.cov(
        falsos, rowvar=False
    ) * (len(falsos) - 1)
    Sw = Sw / max(n - 2, 1) + np.eye(X.shape[1]) * 1e-3

    w = np.linalg.solve(Sw, mu1 - mu0)
    projecoes = X @ w

    # Limiar que separa melhor as duas nuvens projetadas.
    candidatos = np.sort(projecoes)
    melhor_limiar, melhor_acuracia = float(projecoes.mean()), -1.0
    for i in range(len(candidatos) - 1):
        corte = float((candidatos[i] + candidatos[i + 1]) / 2)
        acuracia = float(((projecoes >= corte).astype(int) == y).mean())
        if acuracia > melhor_acuracia:
            melhor_acuracia, melhor_limiar = acuracia, corte

    return w, 0.0, melhor_limiar


def leave_one_out(X: np.ndarray, y: np.ndarray) -> float:
    """Acuracia honesta com amostra pequena: treina em n-1, testa no que sobrou."""
    acertos = 0
    for i in range(len(X)):
        mascara = np.ones(len(X), dtype=bool)
        mascara[i] = False
        Xt, yt = X[mascara], y[mascara]
        if len(np.unique(yt)) < 2:
            continue
        w, _, limiar = ajustar_lda(Xt, yt)
        predito = int(float(X[i] @ w) >= limiar)
        acertos += int(predito == y[i])
    return acertos / len(X) if len(X) else 0.0


def main() -> int:
    parser = argparse.ArgumentParser(description="Calibra o detector de vivacidade")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "datasets" / "liveness",
        help="pasta contendo real/ e spoof/",
    )
    args = parser.parse_args()

    if not face_engine.ENGINE_AVAILABLE:
        raise SystemExit(f"Motor facial indisponivel: {face_engine.ENGINE_ERROR}")

    print(f"Extraindo caracteristicas de {args.dataset}")
    reais = extrair(args.dataset / "real")
    falsos = extrair(args.dataset / "spoof")

    if len(reais) < 5 or len(falsos) < 5:
        raise SystemExit(
            "\nPoucas amostras para calibrar (minimo pratico: 5 de cada, "
            "recomendado 20+).\nColete mais imagens e rode de novo -- ate la a "
            "verificacao de vivacidade fica desligada, que e o padrao seguro."
        )

    X = np.vstack([reais, falsos])
    y = np.concatenate([np.ones(len(reais)), np.zeros(len(falsos))]).astype(int)

    # Normaliza para que nenhuma caracteristica domine so por causa da escala
    # (nitidez chega a milhares; "especular" fica entre 0 e 1).
    media, escala = X.mean(axis=0), X.std(axis=0)
    Z = (X - media) / np.where(escala == 0, 1.0, escala)

    w, b, limiar = ajustar_lda(Z, y)
    projecoes = Z @ w
    predito = (projecoes >= limiar).astype(int)

    acuracia = float((predito == y).mean())
    # Num sistema de acesso: "falso aceito" = spoof aprovado como real.
    spoof_aceito = float(((predito == 1) & (y == 0)).sum() / max((y == 0).sum(), 1))
    real_rejeitado = float(((predito == 0) & (y == 1)).sum() / max((y == 1).sum(), 1))
    loo = leave_one_out(Z, y)

    print(f"\nAmostras: {len(reais)} reais, {len(falsos)} falsas")
    print(f"Acuracia (no proprio conjunto) ... {acuracia:.1%}")
    print(f"Acuracia (leave-one-out) ........ {loo:.1%}   <== a que vale citar")
    print(f"Spoofs aceitos como reais ....... {spoof_aceito:.1%}")
    print(f"Reais rejeitados ................ {real_rejeitado:.1%}")

    print("\nPeso de cada caracteristica (normalizada):")
    for nome, peso in sorted(
        zip(liveness.FEATURE_NAMES, w), key=lambda t: abs(t[1]), reverse=True
    ):
        print(f"  {nome:<18} {peso:+.3f}")

    modelo = {
        "w": [float(v) for v in w],
        "b": float(b),
        "limiar": float(limiar),
        "media": [float(v) for v in media],
        "escala": [float(v) for v in escala],
        "acuracia": loo,
        "amostras_reais": len(reais),
        "amostras_falsas": len(falsos),
        "features": list(liveness.FEATURE_NAMES),
    }
    liveness.MODEL_PATH.write_text(
        json.dumps(modelo, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\nModelo gravado em {liveness.MODEL_PATH}")
    print("Reinicie o servidor e ligue LIVENESS_ENABLED=true no .env.")

    if loo < 0.8:
        print(
            "\n[AVISO] Acuracia leave-one-out abaixo de 80%: nao ligue a\n"
            "        verificacao em producao ainda. Colete mais amostras, com\n"
            "        mais variacao de iluminacao e de tipo de ataque."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
