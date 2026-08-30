"""Testes da deteccao de presenca (G2).

Os detectores reais do OpenCV sao usados no caso negativo (quadro vazio, que e
deterministico) e substituidos por dubles nos casos positivos -- depender de uma
cascata Haar achar rosto numa imagem sintetica deixaria o teste instavel.
"""
from __future__ import annotations

import numpy as np
import pytest

import presence


class FakeCascade:
    """Cascata que sempre devolve a mesma caixa (x, y, w, h)."""

    def __init__(self, box=None):
        self._box = [] if box is None else np.array([box])

    def detectMultiScale(self, *args, **kwargs):
        return self._box

    def empty(self):
        return False


def test_quadro_vazio_nao_acusa_presenca():
    """Falso positivo aqui poluiria o histórico com eventos inexistentes."""
    preto = np.zeros((480, 640, 3), dtype=np.uint8)
    assert presence.detect(preto) == {"presente": False, "fonte": "", "box": None}


def test_rosto_frontal_encontrado(monkeypatch):
    monkeypatch.setattr(presence, "_frontal", FakeCascade((100, 50, 80, 80)))
    achado = presence.detect(np.zeros((480, 640, 3), dtype=np.uint8))

    assert achado["presente"] is True
    assert achado["fonte"] == presence.FONTE_FRONTAL
    # Mesma convencao do face_recognition: [topo, direita, base, esquerda].
    assert achado["box"] == [50, 180, 130, 100]


def test_perfil_espelhado_devolve_caixa_na_imagem_original(monkeypatch):
    """A cascata de perfil so pega um lado; o outro vem da imagem espelhada."""
    monkeypatch.setattr(presence, "_frontal", FakeCascade())
    chamadas = {"n": 0}

    class PerfilSoNoEspelho:
        def detectMultiScale(self, *args, **kwargs):
            chamadas["n"] += 1
            # Vazio na imagem original, achado na espelhada.
            return [] if chamadas["n"] == 1 else np.array([(10, 40, 60, 60)])

        def empty(self):
            return False

    monkeypatch.setattr(presence, "_profile", PerfilSoNoEspelho())
    achado = presence.detect(np.zeros((480, 640, 3), dtype=np.uint8))

    assert achado["fonte"] == presence.FONTE_PERFIL
    # x espelhado de volta: 640 - 10 - 60 = 570
    assert achado["box"] == [40, 630, 100, 570]


def test_corpo_e_o_ultimo_recurso(monkeypatch):
    monkeypatch.setattr(presence, "_frontal", FakeCascade())
    monkeypatch.setattr(presence, "_profile", FakeCascade())

    class HogFalso:
        def detectMultiScale(self, *args, **kwargs):
            return (np.array([(20, 30, 100, 200)]), None)

    monkeypatch.setattr(presence, "_hog", HogFalso())
    assert presence.detect(np.zeros((480, 640, 3), dtype=np.uint8))["fonte"] == (
        presence.FONTE_CORPO
    )


def test_sem_opencv_a_deteccao_apenas_se_cala(monkeypatch):
    """Degradacao controlada: sem detectores, o pipeline volta ao no_face."""
    monkeypatch.setattr(presence, "PRESENCE_AVAILABLE", False)
    assert presence.detect(np.zeros((10, 10, 3), dtype=np.uint8))["presente"] is False


def test_quadro_corrompido_nao_derruba_o_pipeline(monkeypatch):
    class Explode:
        def detectMultiScale(self, *args, **kwargs):
            raise RuntimeError("quadro invalido")

        def empty(self):
            return False

    monkeypatch.setattr(presence, "_frontal", Explode())
    assert presence.detect(np.zeros((480, 640, 3), dtype=np.uint8))["presente"] is False


def test_stats_reporta_o_estado():
    estado = presence.stats()
    assert set(estado) == {"available", "error"}
