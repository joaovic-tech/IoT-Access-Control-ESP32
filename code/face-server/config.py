"""Carrega a configuracao do servidor a partir de variaveis de ambiente / .env."""
from __future__ import annotations

import os
from pathlib import Path

try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:  # pragma: no cover - dotenv e opcional
    pass

BASE_DIR = Path(__file__).resolve().parent
KNOWN_FACES_DIR = BASE_DIR / "known_faces"
DETECTIONS_DIR = BASE_DIR / "detections"
PEOPLE_DIR = BASE_DIR / "people"
ROOMS_DIR = BASE_DIR / "rooms"
# Somente leitura: os niveis de acesso deixaram de existir (os comodos ficam na
# propria pessoa). A pasta ainda e lida para migrar cadastros antigos.
LEVELS_DIR = BASE_DIR / "levels"


def _get_bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on", "sim"}


HOST: str = os.getenv("HOST", "0.0.0.0")
PORT: int = int(os.getenv("PORT", "8000"))

# Tolerancia do face_recognition: menor = mais rigoroso. ~0.6 e o padrao da lib.
TOLERANCE: float = float(os.getenv("TOLERANCE", "0.6"))

# Minimo de amostras para considerar uma pessoa "ativa" no reconhecimento.
MIN_SAMPLES_ACTIVE: int = int(os.getenv("MIN_SAMPLES_ACTIVE", "3"))

# Largura maxima analisada pelo detector de rostos. O custo do HOG cresce com a
# area: uma foto de celular (2544 px) leva ~7,5 s e a mesma em 1024 px leva
# ~1,6 s, achando o mesmo rosto. 0 desliga a reducao.
MAX_ANALYSIS_WIDTH: int = int(os.getenv("MAX_ANALYSIS_WIDTH", "1024"))

# Se True e um rosto autorizado for reconhecido, chama {esp32_url}/api/unlock.
AUTO_UNLOCK: bool = _get_bool("AUTO_UNLOCK", False)

# URL padrao do ESP32-CAM (usada por /scan e /enroll quando o cliente nao informa).
ESP32_DEFAULT_URL: str = os.getenv("ESP32_DEFAULT_URL", "http://192.168.15.72")

# Token opcional exigido pelas rotas de atuadores do ESP32 (?key=).
ESP32_ACCESS_TOKEN: str | None = os.getenv("ESP32_ACCESS_TOKEN") or None

# Giro aplicado ao quadro ANTES do reconhecimento, em graus horarios
# (0/90/180/270). O detector do dlib nao acha rosto de cabeca para baixo, entao
# uma camera montada invertida precisa de ROTATION=180 aqui. Vale para o fluxo
# do PIR (o firmware nao informa orientacao); o app manda o valor por requisicao.
DEFAULT_ROTATION: int = int(os.getenv("ROTATION", "0"))

# Comodo vigiado por esta camera quando o cliente nao informa -- e o caso do
# firmware no evento do PIR, que so envia a foto. O app manda o comodo
# cadastrado no dispositivo a cada requisicao e esse valor tem prioridade.
DEFAULT_ROOM: str = os.getenv("ROOM", "")

# Deteccao de presenca (G2): quando o reconhecimento nao acha rosto, detectores
# auxiliares do OpenCV verificam se ha alguem no quadro (rosto de perfil, corpo).
# So roda nesse caso, entao nao pesa na deteccao bem-sucedida.
PRESENCE_FALLBACK: bool = _get_bool("PRESENCE_FALLBACK", True)

# Verificacao de vivacidade (G1): barra foto impressa/tela mostrada a camera.
# Exige liveness_model.json gerado por tools/liveness_calibrate.py. Sem modelo,
# ou com LIVENESS_ENABLED=false, a checagem nao opina e nada muda.
LIVENESS_ENABLED: bool = _get_bool("LIVENESS_ENABLED", False)

# --- Assistente do administrador (OPCIONAL, G3) ---
# Modelo local no Ollama que traduz perguntas em portugues em consultas ao
# historico ou em PROPOSTAS de mudanca de configuracao. Nunca decide acesso:
# isso e deterministico em access_control.py (tasks.md secao 3.2).
OLLAMA_URL: str = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434")
OLLAMA_MODEL: str | None = os.getenv("OLLAMA_MODEL") or None
OLLAMA_ENABLED: bool = bool(OLLAMA_MODEL)
OLLAMA_TIMEOUT: float = float(os.getenv("OLLAMA_TIMEOUT", "60"))

# Telegram e OPCIONAL e fica desligado sem token/chat id.
TELEGRAM_BOT_TOKEN: str | None = os.getenv("TELEGRAM_BOT_TOKEN") or None
TELEGRAM_CHAT_ID: str | None = os.getenv("TELEGRAM_CHAT_ID") or None
TELEGRAM_ENABLED: bool = bool(TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID)

# Timeout (s) para chamadas HTTP saindo do servidor (ESP32, Telegram).
HTTP_TIMEOUT: float = float(os.getenv("HTTP_TIMEOUT", "5"))


def ensure_dirs() -> None:
    for d in (KNOWN_FACES_DIR, DETECTIONS_DIR, PEOPLE_DIR, ROOMS_DIR):
        d.mkdir(parents=True, exist_ok=True)
