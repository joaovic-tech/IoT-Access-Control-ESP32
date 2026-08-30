"""Testes do giro de quadro usado antes do reconhecimento.

`normalize_rotation` nao depende de nada; `rotate_jpeg` precisa do OpenCV e e
pulado quando o motor nao esta instalado (mesmo criterio do resto da suite).
"""
from __future__ import annotations

import pytest

import face_engine


@pytest.mark.parametrize(
    ("entrada", "esperado"),
    [
        (0, 0),
        (90, 90),
        (180, 180),
        (270, 270),
        (-90, 270),
        (450, 90),
        (360, 0),
        # Angulos que o pipeline nao sabe girar caem para "sem giro".
        (45, 0),
        (1000, 0),
    ],
)
def test_normalize_rotation(entrada, esperado):
    assert face_engine.normalize_rotation(entrada) == esperado


def _jpeg_com_marca():
    """JPEG preto com um quadrado branco no canto superior esquerdo."""
    cv2 = pytest.importorskip("cv2", reason="OpenCV nao instalado")
    np = pytest.importorskip("numpy")

    img = np.zeros((80, 120, 3), dtype=np.uint8)
    img[0:20, 0:20] = 255
    ok, encoded = cv2.imencode(".jpg", img)
    assert ok
    return cv2, np, encoded.tobytes()


def test_rotate_jpeg_180_leva_a_marca_para_o_canto_oposto():
    cv2, np, data = _jpeg_com_marca()

    girado = face_engine.rotate_jpeg(data, 180)
    img = cv2.imdecode(np.frombuffer(girado, np.uint8), cv2.IMREAD_COLOR)

    assert img.shape == (80, 120, 3)  # 180 nao troca largura/altura
    assert img[-10, -10].mean() > 200  # marca agora no canto inferior direito
    assert img[10, 10].mean() < 60  # canto original ficou preto


def test_rotate_jpeg_90_troca_largura_e_altura():
    cv2, np, data = _jpeg_com_marca()

    girado = face_engine.rotate_jpeg(data, 90)
    img = cv2.imdecode(np.frombuffer(girado, np.uint8), cv2.IMREAD_COLOR)

    assert img.shape == (120, 80, 3)


def test_rotate_jpeg_sem_giro_devolve_os_mesmos_bytes():
    # Atalho importante: sem giro nao ha decode/encode, entao a rota funciona
    # mesmo com um payload que o OpenCV nao saberia abrir.
    payload = b"\xff\xd8\xff-nao-e-um-jpeg-valido"
    assert face_engine.rotate_jpeg(payload, 0) is payload


def test_rotate_jpeg_recusa_imagem_invalida():
    pytest.importorskip("cv2", reason="OpenCV nao instalado")
    with pytest.raises(ValueError):
        face_engine.rotate_jpeg(b"nao-e-imagem", 180)
