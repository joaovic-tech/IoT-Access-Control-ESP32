"""G4 -- varredura do limiar de reconhecimento (FAR x FRR).

Material de TCC, nao faz parte do servidor: nenhum modulo do face-server importa
este arquivo.

O que faz
---------
Gera os encodings de um conjunto de rostos organizados por pessoa, monta todos
os pares possiveis e varre o limiar de decisao medindo:

    FRR (False Rejection Rate) -- pares da MESMA pessoa com distancia > limiar
    FAR (False Acceptance Rate) -- pares de pessoas DIFERENTES com distancia <= limiar

O ponto onde as duas curvas se cruzam e o EER (Equal Error Rate), a metrica
classica para comparar sistemas biometricos.

Uso
---
    # com as amostras cadastradas no proprio sistema (poucas -- ver AVISO)
    .venv\\Scripts\\python.exe tools/tolerance_experiment.py

    # com um conjunto externo, no formato <pasta>/<pessoa>/<imagens>
    .venv\\Scripts\\python.exe tools/tolerance_experiment.py --dataset C:/datasets/lfw

    # inclui a distribuicao das distancias ja observadas em detections/
    .venv\\Scripts\\python.exe tools/tolerance_experiment.py --com-historico

AVISO METODOLOGICO
------------------
Com poucas pessoas cadastradas o numero de pares e minusculo e a curva NAO tem
significancia estatistica. Para o TCC, rode com um conjunto publico (LFW:
http://vis-www.cs.umass.edu/lfw/) ou cadastre mais voluntarios, e registre no
texto quantos pares sustentam cada ponto da curva.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from itertools import combinations
from pathlib import Path

# Permite rodar de dentro de tools/ ou da raiz do modulo.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import face_engine  # noqa: E402
from config import DETECTIONS_DIR, KNOWN_FACES_DIR, TOLERANCE  # noqa: E402

VALID_EXT = (".jpg", ".jpeg", ".png", ".webp")


def carregar_encodings(raiz: Path) -> dict[str, list]:
    """Le <raiz>/<pessoa>/<imagens> e devolve {pessoa: [encodings]}."""
    if not raiz.is_dir():
        raise SystemExit(f"Pasta nao encontrada: {raiz}")

    por_pessoa: dict[str, list] = {}
    for pasta in sorted(p for p in raiz.iterdir() if p.is_dir()):
        encodings = []
        for img in sorted(pasta.iterdir()):
            if img.suffix.lower() not in VALID_EXT:
                continue
            try:
                encs, _ = face_engine._encodings_from_bytes(img.read_bytes())
            except Exception as exc:
                print(f"  [WARN] {img.name}: {exc}")
                continue
            if not encs:
                print(f"  [WARN] nenhum rosto detectado em {img.name}")
                continue
            encodings.append(encs[0])
        if encodings:
            por_pessoa[pasta.name] = encodings
            print(f"  [OK] {pasta.name}: {len(encodings)} rosto(s)")
    return por_pessoa


def montar_pares(por_pessoa: dict[str, list]) -> tuple[list[float], list[float]]:
    """Distancias dos pares genuinos (mesma pessoa) e impostores (pessoas diferentes)."""
    import numpy as np

    genuinos: list[float] = []
    impostores: list[float] = []

    for encodings in por_pessoa.values():
        for a, b in combinations(encodings, 2):
            genuinos.append(float(np.linalg.norm(a - b)))

    pessoas = list(por_pessoa)
    for p1, p2 in combinations(pessoas, 2):
        for a in por_pessoa[p1]:
            for b in por_pessoa[p2]:
                impostores.append(float(np.linalg.norm(a - b)))

    return genuinos, impostores


def varrer(genuinos: list[float], impostores: list[float]) -> list[dict]:
    """FAR e FRR para cada limiar de 0.30 a 0.70."""
    linhas = []
    limiar = 0.30
    while limiar <= 0.7001:
        frr = (
            sum(1 for d in genuinos if d > limiar) / len(genuinos) if genuinos else None
        )
        far = (
            sum(1 for d in impostores if d <= limiar) / len(impostores)
            if impostores
            else None
        )
        linhas.append({"limiar": round(limiar, 2), "far": far, "frr": frr})
        limiar += 0.02
    return linhas


def encontrar_eer(linhas: list[dict]) -> dict | None:
    """Ponto de menor diferenca entre FAR e FRR (aproximacao do EER)."""
    validas = [l for l in linhas if l["far"] is not None and l["frr"] is not None]
    if not validas:
        return None
    return min(validas, key=lambda l: abs(l["far"] - l["frr"]))


def distribuicao_do_historico() -> None:
    """Distancias realmente observadas em operacao (sem verdade de referencia)."""
    arquivos = sorted(DETECTIONS_DIR.glob("*.json")) if DETECTIONS_DIR.is_dir() else []
    distancias = []
    for caminho in arquivos:
        try:
            registro = json.loads(caminho.read_text(encoding="utf-8"))
        except Exception:
            continue
        if isinstance(registro.get("distance"), (int, float)):
            distancias.append((registro["distance"], registro.get("status", "")))

    if not distancias:
        print("\nNenhuma distancia registrada em detections/.")
        return

    reconhecidos = [d for d, s in distancias if s == "recognized"]
    desconhecidos = [d for d, s in distancias if s == "unknown"]
    print(f"\n--- Distancias observadas em operacao ({len(distancias)} deteccoes) ---")
    for rotulo, valores in (("recognized", reconhecidos), ("unknown", desconhecidos)):
        if valores:
            print(
                f"  {rotulo:<12} n={len(valores):<4} "
                f"min={min(valores):.3f}  media={sum(valores)/len(valores):.3f}  "
                f"max={max(valores):.3f}"
            )
    print(
        "  OBS: sao dados sem verdade de referencia (ninguem confirmou quem era\n"
        "       de fato cada rosto), entao servem como descricao, nao como FAR/FRR."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Varredura do limiar (FAR x FRR)")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=KNOWN_FACES_DIR,
        help="pasta no formato <pessoa>/<imagens> (padrao: known_faces/)",
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=Path(__file__).resolve().parent / "tolerance_curve.csv",
        help="arquivo CSV de saida",
    )
    parser.add_argument(
        "--com-historico",
        action="store_true",
        help="mostra tambem as distancias ja registradas em detections/",
    )
    args = parser.parse_args()

    if not face_engine.ENGINE_AVAILABLE:
        raise SystemExit(f"Motor facial indisponivel: {face_engine.ENGINE_ERROR}")

    print(f"Lendo rostos de {args.dataset}")
    por_pessoa = carregar_encodings(args.dataset)
    if len(por_pessoa) < 2:
        print(
            "\n[AVISO] Menos de 2 pessoas com rosto detectado: sem pares impostores,\n"
            "        nao da para calcular FAR. Use --dataset com um conjunto maior."
        )

    genuinos, impostores = montar_pares(por_pessoa)
    print(
        f"\nPessoas: {len(por_pessoa)} | "
        f"pares genuinos: {len(genuinos)} | pares impostores: {len(impostores)}"
    )
    if len(genuinos) < 30 or len(impostores) < 30:
        print(
            "[AVISO] Amostra pequena demais para uma curva com significancia\n"
            "        estatistica. Registre esta limitacao no TCC ou use LFW."
        )

    linhas = varrer(genuinos, impostores)

    print(f"\n{'limiar':>7} {'FAR':>9} {'FRR':>9}   (limiar atual do sistema: {TOLERANCE})")
    for linha in linhas:
        far = "-" if linha["far"] is None else f"{linha['far']:.3f}"
        frr = "-" if linha["frr"] is None else f"{linha['frr']:.3f}"
        marca = "  <== TOLERANCE" if abs(linha["limiar"] - TOLERANCE) < 0.01 else ""
        print(f"{linha['limiar']:>7.2f} {far:>9} {frr:>9}{marca}")

    eer = encontrar_eer(linhas)
    if eer:
        print(
            f"\nEER aproximado no limiar {eer['limiar']:.2f} "
            f"(FAR={eer['far']:.3f}, FRR={eer['frr']:.3f})"
        )

    args.csv.parent.mkdir(parents=True, exist_ok=True)
    with args.csv.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=["limiar", "far", "frr"])
        writer.writeheader()
        writer.writerows(linhas)
    print(f"\nCurva salva em {args.csv}")

    if args.com_historico:
        distribuicao_do_historico()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
