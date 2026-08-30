"""Testes da verificacao de vivacidade (G1).

Nao ha modelo calibrado no repositorio (ele depende das amostras de cada
instalacao), entao os testes cobrem o contrato: extracao de caracteristicas,
pontuacao com um modelo sintetico e -- o mais importante para seguranca -- o
comportamento quando a verificacao esta desligada.
"""
from __future__ import annotations

import json

import numpy as np
import pytest

import liveness


@pytest.fixture
def modelo_sintetico(monkeypatch):
    """Modelo que pontua pela primeira caracteristica (nitidez)."""
    w = [1.0] + [0.0] * (len(liveness.FEATURE_NAMES) - 1)
    monkeypatch.setattr(
        liveness,
        "_model",
        {
            "w": w,
            "b": 0.0,
            "limiar": 0.0,
            "media": [0.0] * len(liveness.FEATURE_NAMES),
            "escala": [1.0] * len(liveness.FEATURE_NAMES),
            "acuracia": 0.9,
        },
    )
    return w


def _quadro_com_textura(seed: int = 0):
    """Ruido colorido: tem microtextura, como um rosto real de perto."""
    rng = np.random.default_rng(seed)
    return rng.integers(0, 255, (200, 200, 3), dtype=np.uint8)


def test_features_tem_a_forma_do_contrato():
    x = liveness.features(_quadro_com_textura())
    assert x.shape == (len(liveness.FEATURE_NAMES),)
    assert np.isfinite(x).all()


def test_quadro_liso_tem_menos_nitidez_que_quadro_texturizado():
    """A nitidez é a pista central: reimpressão perde microtextura."""
    liso = np.full((200, 200, 3), 128, dtype=np.uint8)
    nitidez_lisa = liveness.features(liso)[0]
    nitidez_textura = liveness.features(_quadro_com_textura())[0]
    assert nitidez_lisa < nitidez_textura


def test_recorte_usa_a_caixa_do_rosto():
    quadro = _quadro_com_textura()
    # Caixa válida e caixa degenerada devem ambas produzir vetor utilizável.
    assert liveness.features(quadro, [50, 150, 150, 50]).shape[0] == len(
        liveness.FEATURE_NAMES
    )
    assert liveness.features(quadro, [0, 5, 5, 0]).shape[0] == len(liveness.FEATURE_NAMES)


def test_desligado_nao_opina_e_nao_bloqueia(monkeypatch):
    """FALHA ABERTA: sem modelo, ninguém pode ser barrado por vivacidade."""
    monkeypatch.setattr(liveness, "_model", None)
    resultado = liveness.check(_quadro_com_textura())

    assert resultado == {"vivo": None, "score": None, "limiar": None}
    assert liveness.enabled() is False
    assert liveness.score(_quadro_com_textura()) is None


def test_com_modelo_devolve_veredito(modelo_sintetico):
    resultado = liveness.check(_quadro_com_textura())
    assert resultado["vivo"] in (True, False)
    assert isinstance(resultado["score"], float)
    assert liveness.enabled() is True


def test_modelo_invalido_desliga_a_verificacao(tmp_path, monkeypatch):
    """Arquivo corrompido não pode derrubar o servidor nem trancar a porta."""
    arquivo = tmp_path / "liveness_model.json"
    arquivo.write_text(json.dumps({"w": [1.0, 2.0]}), encoding="utf-8")  # tamanho errado
    monkeypatch.setattr(liveness, "MODEL_PATH", arquivo)

    assert liveness.load_model() is None
    assert liveness.enabled() is False


def test_sem_arquivo_de_modelo(tmp_path, monkeypatch):
    monkeypatch.setattr(liveness, "MODEL_PATH", tmp_path / "nao-existe.json")
    assert liveness.load_model() is None


def test_stats_reporta_o_estado(modelo_sintetico):
    estado = liveness.stats()
    assert estado["enabled"] is True
    assert estado["accuracy"] == 0.9
