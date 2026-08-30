# Tarefas do Projeto — IoT Access Control (ESP32-CAM + App Kotlin + Reconhecimento Facial)

> [!NOTE]
> Reescrito em **29/08/2026** após a **validação em campo**: firmware, servidor
> Python e app rodando juntos na LAN, com reconhecimento facial confirmado em
> duas pessoas diferentes e em várias posições.
> Branch de trabalho: `feature/esp32cam-stream-kotlin-app`.
>
> Os blocos **A**, **B**, **C**, **D** e **E** (exceto E2) estão concluídos.
> O sistema saiu do estado "compila e passa nos testes" para **"funciona no
> hardware real"**.

---

## 1. Arquitetura entregue

```
┌─────────────┐   MJPEG :81/stream          ┌─────────────────┐
│  ESP32-CAM  │ ──────────────────────────▶ │   App Kotlin     │
│ (AI-Thinker)│   REST :80/api/* (flash,    │  (Android)       │
│  + PIR      │ ◀── relay, unlock, status) ─│                  │
│  + Relé     │                             └───────┬──────────┘
└──────┬──────┘                                     │ REST (pessoas,
       │ /capture (JPEG sob demanda)                │ detecções, scan)
       │ POST /detect (evento do PIR)               ▼
       ▼
┌────────────────────────────────────────────────────────────┐
│  Servidor Python (PC na LAN)  —  FastAPI + OpenCV +         │
│  face_recognition (dlib)                                   │
│  • gira o quadro ANTES de detectar (câmera invertida)      │
│  • known_faces/  (amostras cadastradas)                    │
│  • detections/   (histórico com carimbo de data/hora)      │
│  • /detect  /people  /enroll  /scan  /detections           │
└────────────────────────────────────────────────────────────┘
```

O **vídeo ao vivo vai direto do ESP32 para o app**; o **reconhecimento facial**
fica no servidor Python, que recebe o `POST /detect` do firmware (PIR) ou puxa
`/capture` quando o app pede um scan.

---

## 2. Situação atual

### Firmware — `code/CameraWebServer/` ✅ validado em campo
- Câmera OV2640 (AI-Thinker), VGA, JPEG, buffers em PSRAM. ~7–23 fps medidos.
- Wi-Fi STA com reconexão no `loop()`; segredos em `arduino_secrets.h`.
- HTTP na porta 80 + stream na 81: `/stream`, `/capture`, `/bmp`, `/api/status`,
  `/api/flash`, `/api/relay`, `/api/unlock`, `/api/scan`, `/control`, `/status`.
- PIR (GPIO 13) com debounce; relé no GPIO 14.
- `face_client.cpp`: task FreeRTOS que faz `POST multipart /detect` no servidor.
- Compila limpo: **35% da flash, 22% da RAM**.

### Servidor Python — `code/face-server/` ✅ motor facial real rodando
- FastAPI + OpenCV + `face_recognition`, com degradação controlada (sem dlib
  o servidor sobe, `/health` reporta `engine_available: false` e as rotas de
  reconhecimento respondem 503).
- **Giro de quadro antes da detecção** (`face_engine.rotate_jpeg`) — ver L10.
- Rotas: `/health`, `/reload`, `/detect`, `/scan`, `/detections`, `/people`
  (GET/POST/PATCH/DELETE), `/people/{id}/samples`, `/enroll/from-esp32`,
  estáticos em `/media/detections/`.
- Persistência em disco: `people/<id>.json`, `known_faces/<id>/*.jpg`,
  `detections/<carimbo>.jpg` + sidecar JSON (limite de 500 registros).
- **38 testes `pytest`** com o motor facial substituído por um dublê.

### App — `code/ESPCamApp/` ✅ usado no celular real
- 4 telas em Jetpack Compose ligadas às APIs reais, ViewModel + `UiState` por
  tela, container de DI manual em `AppContainer`.
- Rede: OkHttp + Retrofit + kotlinx-serialization, `@Url` absoluta, **dois
  perfis de timeout** (curto para consultas, longo para visão computacional).
- Player MJPEG próprio com descarte de quadros atrasados, reconexão automática
  e **giro de exibição** (0/90/180/270).
- `SettingsStore` (DataStore): dispositivos ESP32 + URL do servidor de faces.
- **18 testes unitários** + 4 instrumentados de navegação.

### Documentação
- `README.md` na raiz (arquitetura, execução, contrato de API);
  `code/face-server/README.md` com o passo a passo do servidor.
- `docs/` é somente-leitura para agentes — `docs/NOTE.md` é atualizado pelo autor.

---

## 3. Lacunas do levantamento inicial

| # | Lacuna | Situação |
|---|--------|----------|
| L1 | Credenciais Wi-Fi e IP do servidor hardcoded | ✅ `arduino_secrets.h` + `secrets_config.h` |
| L2 | Firmware não enviava foto no evento do PIR | ✅ `face_client.cpp` (task FreeRTOS) |
| L3 | Servidor Python inexistente | ✅ `code/face-server/` |
| L4 | App sem camada de rede / permissão `INTERNET` | ✅ manifest + Retrofit/OkHttp |
| L5 | App sem player de MJPEG | ✅ `MjpegPlayer` |
| L6 | App sem CRUD de pessoas / upload de amostras | ✅ telas Pessoas e Cadastrar |
| L7 | Sem contrato de API definido | ✅ `README.md` §4 |
| L8 | GPIO 12 (relé) é strapping pin | ✅ relé movido para GPIO 14 |
| L9 | Sem guia de setup, sem README na raiz | ✅ README na raiz (guia em `docs/` = E2) |
| **L10** | **Câmera montada invertida → dlib nunca achava rosto** | ✅ **giro no servidor (29/08)** |
| **L11** | **Timeout curto abortava o cadastro de pessoas** | ✅ **cliente HTTP paciente (29/08)** |
| **L12** | **Lista de Pessoas nunca recarregava** | ✅ **refresh ao entrar na tela (29/08)** |

---

## 4. BLOCO A — Firmware ESP32-CAM ✅

- [x] **A1.** Externalizar segredos (`arduino_secrets.h` + `.example`, no `.gitignore`).
- [x] **A2.** Corrigir pino do relé — GPIO 12 → **GPIO 14** (L8).
- [x] **A3.** Envio de foto ao servidor no evento do PIR (task FreeRTOS, cooldown 10 s).
- [x] **A4.** Endpoint `/api/scan` (disparo manual pelo app).
- [x] **A5.** `/api/status` com `fps`, `stream_clients`, versão do firmware e `has_psram`.
- [x] **A6.** CORS restrito + token opcional (`?key=`) nos atuadores.
- [x] **A7.** Compilação validada com `arduino-cli` (FQBN `esp32:esp32:esp32cam`).

---

## 5. BLOCO B — Servidor Python + OpenCV ✅

- [x] **B1.** Estrutura do módulo (`main.py`, `requirements.txt`, `.env.example`, ...).
- [x] **B2.** Carga de rostos conhecidos + `POST /reload`.
- [x] **B3.** `POST /detect` — decodifica, compara, grava em `detections/`.
- [x] **B4.** CRUD de pessoas (`GET/POST/PATCH/DELETE /people`, amostras).
- [x] **B5.** `POST /enroll/from-esp32`.
- [x] **B6.** `GET /detections` paginado + `StaticFiles` em `/media/detections/`.
- [x] **B7.** `POST /scan`.
- [x] **B8.** `GET /health`.
- [x] **B9.** Configuração por `.env` (Telegram opcional, desligado por padrão).
- [x] **B10.** Execução documentada + README do módulo.
- [x] **B11.** **Giro de quadro antes do reconhecimento** — `rotate_jpeg()` em
  `/detect`, `/scan` e `/enroll/from-esp32`; padrão do PIR vem de `ROTATION` no
  `.env`, e um valor explícito na requisição tem prioridade.

---

## 6. BLOCO C — App Kotlin ✅

- [x] **C1.** Permissões e `network_security_config.xml`.
- [x] **C2.** Dependências (OkHttp, Retrofit, Coil, DataStore, lifecycle).
- [x] **C3.** Camada de dados (`remote/`, `repo/`, `settings/`, mapeadores DTO→domínio).
- [x] **C4.** Player MJPEG com reconexão e descarte de quadros atrasados.
- [x] **C5.** Tela Principal ligada ao real (stream + scan + detecções).
- [x] **C6.** Tela Dispositivos ligada ao real (status, flash, cadastro).
- [x] **C7.** Tela Pessoas ligada ao real (busca, badge, remoção).
- [x] **C8.** Tela Cadastrar Pessoa (galeria ou captura pelo ESP32-CAM).
- [x] **C9.** Botão de liberar acesso com confirmação.
- [x] **C10.** Timeouts, retry/backoff e mensagens de erro amigáveis.
- [x] **C11.** `ViewModel` por tela + `UiState`, sem dados mock.
- [x] **C12.** **Editar dispositivo** (nome, IP, portas) — a conexão só é testada
  quando o endereço muda.
- [x] **C13.** **Orientação por dispositivo** (0/90/180/270) — gira a exibição do
  MJPEG e acompanha `/scan` e `/enroll/from-esp32`.
- [x] **C14.** **Moldura por status** nas detecções: verde + nome (reconhecido),
  vermelha + "Desconhecido", cinza + "Sem rosto".
- [x] **C15.** **Perfis de timeout separados** — 8 s nas consultas, 90 s nas rotas
  de visão computacional (L11).
- [x] **C16.** **Refresh da lista de Pessoas** ao entrar na tela (L12).
- [x] **C17.** **Diagnóstico de rede na UI** — o erro nomeia o endereço tentado e
  o botão do servidor faz `GET /health` antes de salvar.

---

## 7. BLOCO D — Integração ponta a ponta ✅

- [x] **D1.** Contrato de API documentado (`README.md` §4).
- [x] **D2.** **Fluxo de cadastro validado** — `POST /people` com amostras →
  `known_faces/<id>/` gravado → recarga dos encodings → pessoa listada no app.
- [x] **D3.** **Fluxo de reconhecimento validado** — scan e PIR geram detecção
  visível em "Rostos detectados recentes".
- [x] **D4.** **Fluxo de acesso** — rosto `recognized` + `authorized` registrado
  no log (`[ACESSO] ...`) e no arquivo em `detections/`.
- [x] **D5.** **Teste de rede real** — ESP32, servidor (PC) e celular na mesma
  LAN; reconhecimento confirmado com **duas pessoas** e em **várias posições**.

---

## 8. BLOCO E — Documentação, segurança e testes

- [x] **E1.** `README.md` na raiz.
- [ ] **E2.** Guia de setup em `docs/setup_guide.md` _(bloqueado: `docs/` é
  somente-leitura para agentes — feito manualmente pelo autor)_.
- [x] **E3.** Segurança — nenhum segredo em código; log de acesso com sucesso e falha.
- [x] **E4.** Testes — firmware (compilação), servidor (38), app (18 + 4).
- [x] **E5.** `.gitignore` cobrindo `arduino_secrets.h`, `.env`, `.venv/`,
  `detections/*`, `known_faces/*/` e `people/*.json` (dado biométrico).

---

## 9. Configuração desta instalação

Valores específicos deste ambiente — **conferir antes de rodar em outra rede**:

| Item | Valor | Onde |
|---|---|---|
| IP do PC (servidor) | `192.168.15.6` | app (aba Dispositivos) + `FACE_SERVER_URL` |
| IP do ESP32-CAM | **muda a cada boot** (DHCP) | app (aba Dispositivos) |
| Orientação da câmera | `180` (montada invertida) | app (por dispositivo) + `ROTATION` no `.env` |
| Porta do servidor | `8000` | precisa de regra de entrada no firewall do Windows |

> [!WARNING]
> O `FACE_SERVER_URL` é **compilado no firmware**: se o IP do PC mudar, é preciso
> regravar a placa. O ESP32-CAM também aceita **um único cliente de stream** por
> vez.

---

## 10. Checklist de "pronto" (CLAUDE.md §8)

- [x] Firmware compila sem warnings (35% flash, 22% RAM) e reconecta o Wi-Fi.
- [x] Servidor sobe com o motor facial real (`engine_available: true`).
- [x] App compila e roda sem dados mock, no emulador e no celular físico.
- [x] **Vídeo ao vivo visível no app a partir do ESP32.**
- [x] **Cadastro de pessoa cria amostras e passa a ser reconhecida.**
- [x] **Evento de PIR/scan gera detecção visível no app.**
- [x] **Reconhecimento validado com duas pessoas e em várias posições.**
- [x] Nenhum segredo nem dado biométrico versionado.
- [x] README na raiz e contrato de API documentados.

### Testes automatizados

| Onde | Comando | Situação |
|---|---|---|
| Firmware | `arduino-cli compile --fqbn esp32:esp32:esp32cam code/CameraWebServer` | ✅ compila limpo |
| Servidor | `.venv\Scripts\python.exe -m pytest tests -q` | ✅ 38 testes |
| App (unit) | `gradlew testDebugUnitTest` | ✅ 18 testes |
| App (instrumentado) | `gradlew connectedDebugAndroidTest` | ✅ 4 testes (emulador Pixel 8 Pro, API 37) |

> Os testes instrumentados exigem o aparelho **desbloqueado e com a tela ligada**
> — num celular bloqueado o Compose falha com `No compose hierarchies found`.

---

## 11. Próximos passos sugeridos

- [ ] Miniatura de pessoa cadastrada ainda falta ser coloca na tela de pessoas cadastradas
- [ ] Implementar no servidor de reconhecimento uma IA que pega as informações de escaneamento e verifica as obsevações das pessoas
  - Ex: Pessoa Antonio, obs cadastrado: Antonio só pode ter acesso ao seu quarto, banheiro e cozinha.
  - Ex 2: Pessoa João, obs: cadastrado: acesso a todos os comodos.
- [ ] **E2** — guia de setup em `docs/` (hardware, gravação, venv, cadastro).
- [ ] **Reserva de DHCP** no roteador para o PC e o ESP32-CAM, ou IP estático no
  firmware — hoje o endereço muda a cada boot e quebra a configuração.
- [ ] **`AUTO_UNLOCK`** — validar o pulso do relé em campo com rosto autorizado
  (o código está pronto; falta o teste com a fechadura montada).
- [ ] **Vários dispositivos** — a orientação e o endereço já são por dispositivo;
  falta testar duas câmeras ao mesmo tempo.
- [ ] **Retenção do histórico** — hoje o limite é 500 detecções; avaliar limpeza
  por idade, já que são dados biométricos.
