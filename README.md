# IoT Access Control — ESP32-CAM

Sistema de controle de acesso com **ESP32-CAM**, **reconhecimento facial em
Python/OpenCV** e um **aplicativo Android (Kotlin/Compose)** para monitorar as
câmeras e gerenciar as pessoas autorizadas.

---

## 1. Arquitetura

```
┌─────────────┐   MJPEG :81/stream          ┌──────────────────┐
│  ESP32-CAM  │ ──────────────────────────▶ │   App Kotlin     │
│ (AI-Thinker)│   REST :80/api/* (flash,    │   (Android)      │
│  + PIR      │ ◀── relay, unlock, status) ─│                  │
│  + Relé     │                             └────────┬─────────┘
└──────┬──────┘                                      │ REST (pessoas,
       │ POST /detect (PIR / scan)                   │ detecções, scan)
       │ GET  /capture (sob demanda)                 │
       ▼                                             ▼
┌─────────────────────────────────────────────────────────────┐
│  Servidor Python (PC na LAN) — FastAPI + OpenCV +           │
│  face_recognition                                           │
│  • known_faces/  amostras cadastradas                       │
│  • detections/   histórico com carimbo de data/hora          │
│  • /detect  /people  /enroll  /scan  /detections            │
└─────────────────────────────────────────────────────────────┘
```

Decisões de projeto:

- o **vídeo ao vivo vai direto do ESP32 para o app** — não passa pelo servidor
  Python, o que mantém a latência baixa;
- o **reconhecimento facial fica no servidor Python**, que recebe um JPEG do
  ESP32 quando o PIR dispara, ou puxa `/capture` quando o app pede um scan;
- a decisão de **liberar o acesso** parte do servidor (`AUTO_UNLOCK`) ou de uma
  ação explícita do usuário no app, sempre com registro em log.

---

## 2. Estrutura do repositório

```
.
├── code/
│   ├── CameraWebServer/   # firmware do ESP32-CAM (Arduino/C++)
│   ├── face-server/       # servidor de reconhecimento facial (Python/FastAPI)
│   └── ESPCamApp/         # aplicativo Android (Kotlin/Jetpack Compose)
├── design-app-mobile/     # mockups das telas
├── docs/                  # documentação do projeto
├── tasks.md               # backlog e progresso
└── README.md
```

---

## 3. Como rodar

### 3.1 Firmware — `code/CameraWebServer/`

1. Copie `arduino_secrets.h.example` para `arduino_secrets.h` e preencha
   `WIFI_SSID`, `WIFI_PASS` e `FACE_SERVER_URL` (IP do PC que roda o servidor
   Python). O arquivo real é ignorado pelo git.
2. Instale o core ESP32 (`esp32:esp32`) na IDE Arduino ou pelo `arduino-cli`.
3. Compile:

```bash
arduino-cli compile --fqbn esp32:esp32:esp32cam code/CameraWebServer
```

4. Grave via módulo **ESP32-CAM-MB** (ou FTDI com IO0 em GND) e abra o monitor
   serial a 115200 baud para ver o IP obtido.

**Pinagem** (definida em `iot_access.h`):

| Função | GPIO | Observação |
|---|---|---|
| Sensor PIR | 13 | entrada com debounce de 50 ms |
| Relé / trava | **14** | GPIO 12 foi evitado por ser *strapping pin* (MTDI): em nível alto no boot a placa não inicializa |
| LED flash | 4 | controlado por `ledcWrite` |
| LED vermelho onboard | 33 | *active low*, indica estado do Wi-Fi |

### 3.2 Servidor de faces — `code/face-server/`

Passo a passo completo em [`code/face-server/README.md`](code/face-server/README.md).
Resumo:

```bash
cd code/face-server
python -m venv .venv
```

```bash
.venv\Scripts\activate
```

```bash
pip install -r requirements.txt
```

```bash
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Sem `dlib`/`face_recognition` instalados o servidor ainda sobe em modo
degradado: `/health` e o CRUD funcionam, e as rotas de reconhecimento
respondem 503 com a instrução de instalação.

### 3.3 App Android — `code/ESPCamApp/`

```bash
cd code/ESPCamApp
```

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Na primeira execução, vá em **Dispositivos** e:

1. cadastre o ESP32-CAM (nome, IP, porta 80, porta de stream 81) — o app testa
   a conexão antes de salvar;
2. informe o endereço do servidor Python (ex.: `http://192.168.15.10:8000`).

---

## 4. Contrato de API

### 4.1 ESP32-CAM (porta 80, exceto o stream)

| Rota | Método | Resposta |
|---|---|---|
| `:81/stream` | GET | `multipart/x-mixed-replace` (MJPEG) |
| `/capture` | GET | JPEG único |
| `/api/status` | GET | `{status, fw, ip, rssi, uptime_s, flash, flash_duty, relay, pir, pir_count, free_heap, free_psram, has_psram, framesize, quality, fps, stream_clients, detect_busy, detect_last_status}` |
| `/api/flash?state=1\|0` | GET | `{success, flash, duty}` |
| `/api/relay?state=1\|0` ou `?pulse=<ms>` | GET | `{success, relay}` |
| `/api/unlock` | GET | `{success, message, relay}` — pulso de 3 s |
| `/api/scan` | GET | `{queued, busy, last_status}` — força captura + `POST /detect` |

As rotas de atuadores (`flash`, `relay`, `unlock`) aceitam `?key=<token>`
quando `ACCESS_TOKEN` está definido em `arduino_secrets.h`; sem a macro, a
checagem fica desativada. Aliases sem o prefixo `/api` também existem.

Exemplos:

```bash
curl http://192.168.15.72/api/status
```

```bash
curl "http://192.168.15.72/api/flash?state=1"
```

### 4.2 Servidor Python (porta 8000)

Tabela completa, payloads e exemplos `curl` em
[`code/face-server/README.md`](code/face-server/README.md#4-contrato-da-api).
Resumo: `/health`, `/reload`, `/detect`, `/scan`, `/detections`, `/people`
(GET/POST/PATCH/DELETE), `/people/{id}/samples`, `/enroll/from-esp32` e os
arquivos estáticos em `/media/detections/`.

`/detect`, `/scan` e `/enroll/from-esp32` aceitam **`rotation`** (0/90/180/270):
o servidor gira o quadro antes de detectar. Sem o valor na requisição vale o
`ROTATION` do `.env` — é o caso do firmware no evento do PIR.

---

## 5. Fluxos principais

**Cadastro de rosto** — App (*Cadastrar Pessoa*) → escolhe 3–5 fotos da galeria
ou captura direto pelo ESP32-CAM → `POST /people` (multipart) → servidor grava
`known_faces/<id>/`, valida um rosto por amostra e recarrega os *encodings* →
a pessoa aparece como **Ativo** em *Pessoas*.

**Reconhecimento** — PIR dispara (ou o usuário toca em *Escanear ambiente*) →
JPEG chega em `POST /detect` → **o servidor gira o quadro** (ver `rotation` em
§4.2) → comparação com os *encodings* → imagem e sidecar JSON gravados em
`detections/` → o app mostra a detecção em *Rostos detectados recentes*, com
moldura verde e o nome quando reconhecido, vermelha quando desconhecido.

> [!IMPORTANT]
> O detector do dlib **não encontra rosto de cabeça para baixo**. Se a câmera
> estiver montada invertida ou de lado, ajuste a orientação do dispositivo no
> app **e** o `ROTATION` no `.env` do servidor — sem isso todo scan devolve
> `no_face`.

**Liberação de acesso** — rosto `recognized` **e** pessoa `authorized`:
com `AUTO_UNLOCK=true` o servidor chama `GET /api/unlock` no ESP32 e o relé
pulsa por 3 s. Sem isso, a liberação é manual pelo botão *Liberar acesso* na
aba Dispositivos (com confirmação).

---

## 6. Testes

```bash
cd code/face-server && .venv\Scripts\python.exe -m pytest tests -q
```

```bash
cd code/ESPCamApp && ./gradlew testDebugUnitTest
```

```bash
arduino-cli compile --fqbn esp32:esp32:esp32cam code/CameraWebServer
```

Cobertura atual: pipeline `/detect` (rosto conhecido / desconhecido / sem
rosto), CRUD de pessoas, `/scan`, `/enroll/from-esp32`, o giro de quadro e o
histórico no servidor (**38 testes**); mapeadores DTO→domínio, parser MJPEG e
normalização de orientação no app (**18 unitários + 4 instrumentados**);
compilação no firmware.

Os testes instrumentados (`gradlew connectedDebugAndroidTest`) exigem o aparelho
**desbloqueado e com a tela ligada**.

---

## 7. Segurança

- Nenhum segredo versionado: Wi-Fi e URL do servidor ficam em
  `arduino_secrets.h` (ignorado), e a configuração do servidor em `.env`.
- Amostras faciais (`known_faces/`), metadados (`people/`) e o histórico
  (`detections/`) são **dados biométricos pessoais** e não vão para o git.
- Toda tentativa de acesso — reconhecida ou não — gera uma linha
  `[ACESSO] ...` no log do servidor e um arquivo em `detections/`.
- `AUTO_UNLOCK` vem desligado por padrão: só ligue depois de validar o
  reconhecimento, porque ele aciona a trava física.
- Todo o tráfego é HTTP puro dentro da LAN (o ESP32 não faz TLS). Não exponha
  essas portas para a internet.

---

## 8. Licença

Ver [LICENSE](LICENSE).
