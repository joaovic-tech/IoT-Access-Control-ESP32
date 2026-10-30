# Plano — People Detect com foto

## Resposta curta

- **Dá para fazer?** Sim. Quando o PIR detecta movimento, a ESP32-CAM tira a foto e envia pelo Wi-Fi para o seu PC.
- **Precisa de servidor?** Sim. A ESP32-CAM não consegue gravar direto numa pasta do PC: alguém do lado do PC precisa **receber** a foto e salvá-la. Esse é o servidor em Python.
- **Comunicação:** **HTTP**. Um único servidor atende o dispositivo e o frontend.

---

## Visão geral

```
 ┌──────────────┐   POST /api/photos (foto JPEG)   ┌──────────────────────┐
 │  ESP32-CAM   │ ───────────────────────────────▶ │  Servidor Python     │
 │  + PIR + LED │   POST /api/heartbeat (status)   │  (FastAPI, HTTP)     │
 └──────────────┘                                  │                      │
                                                   │  salva em ./photos/  │
 ┌──────────────┐   GET /api/photos (lista)        │                      │
 │  Frontend    │ ◀──────────────────────────────▶ │                      │
 │  React (web) │   GET /photos/<arquivo>.jpg      └──────────────────────┘
 └──────────────┘
```

Um servidor só, com várias rotas: o dispositivo **envia** e o frontend **consulta**.

---

## Por que HTTP?

- **Enviar foto:** simples, é um `POST` com a imagem.
- **Frontend buscar fotos:** `fetch()` direto na mesma API.
- **Um programa só rodando no PC:** o servidor Python.

---

## Antes de começar: 3 cuidados de hardware

1. **Trocar o pino do LED (obrigatório).** O **GPIO 16** é usado pela memória PSRAM, e a câmera precisa dela. Mova o LED para o **GPIO 14**. O fio verde sai do lado direito e vai para o pino **IO14** (lado esquerdo, `b8` → usar `a8`). Atualizo o desenho quando chegar essa etapa.
2. **Alimentação.** A câmera mais o Wi-Fi puxam picos de corrente. O regulador precisa aguentar **pelo menos 1 A**. Se a placa reiniciar sozinha ao tirar foto (erro *brownout*), é falta de corrente.
3. **Mesma rede.** O PC e a ESP32-CAM precisam estar no mesmo Wi-Fi, e o firewall do PC precisa liberar a porta do servidor (ex.: `8000`).

---

## Estrutura de pastas (repositório `IoT-Access-Control-ESP32`, branch `new-plan`)

```
IoT-Access-Control-ESP32/
├── firmware/
│   └── people-detect/
│       └── people-detect.ino   ← código da ESP32-CAM (movido para cá)
├── server/
│   ├── main.py                 ← servidor FastAPI
│   └── requirements.txt
├── photos/                     ← fotos salvas (criada pelo servidor)
├── web/                        ← frontend React (futuro)
├── assets/                     ← diagramas
└── PLANO.md
```

---

## Rotas do servidor (API)

| Método | Rota | Quem usa | O que faz |
|---|---|---|---|
| `POST` | `/api/photos` | ESP32-CAM | Recebe a foto (JPEG) e salva em `photos/` |
| `POST` | `/api/heartbeat` | ESP32-CAM | "Estou vivo" a cada 30 s (IP, sinal Wi-Fi) |
| `GET` | `/api/photos` | Frontend | Lista as fotos (nome, data, URL) |
| `GET` | `/photos/<nome>.jpg` | Frontend | Entrega o arquivo da imagem |
| `GET` | `/api/device` | Frontend | Online/offline, última foto |

Nome do arquivo: a data e hora da foto, ex.: `2026-10-30_15-42-07.jpg`.

---

## O que muda no código da ESP32-CAM

Hoje o código faz: **PIR → LED**.
Vai passar a fazer:

```
ligar → conectar no Wi-Fi → iniciar câmera → esperar o PIR estabilizar (~60 s)
loop:
  PIR detectou E já passou o "cooldown"?
    → acende LED
    → tira foto
    → envia POST para http://IP_DO_PC:8000/api/photos
    → apaga LED
  a cada 30 s → envia heartbeat
```

- **Cooldown** (ex.: 10 s): evita tirar dezenas de fotos seguidas da mesma pessoa.
- Bibliotecas, todas já vêm com a placa ESP32 na Arduino IDE: `esp_camera.h`, `WiFi.h` e `HTTPClient.h`.

---

## Etapas (uma de cada vez, testando cada uma)

| # | Etapa | Como saber que deu certo |
|---|---|---|
| 0 | Preparar o repositório: clonar, criar a branch `new-plan` e mover os arquivos | `git status` mostra a branch `new-plan` com os arquivos novos |
| 1 | Testar a câmera sozinha com o exemplo **CameraWebServer** da Arduino IDE | Ver a imagem ao vivo no navegador |
| 2 | Mover o LED para o GPIO 14 e testar PIR + LED de novo | Mesmo comportamento de hoje |
| 3 | Criar o servidor Python com `POST /api/photos` | Enviar uma imagem pelo `curl` e ver ela em `photos/` |
| 4 | ESP32-CAM: Wi-Fi + câmera + envio da foto quando o PIR dispara | Passar a mão → foto nova em `photos/` |
| 5 | Rotas para o frontend (`GET /api/photos`, `/photos/...`, heartbeat) | Abrir `http://localhost:8000/docs` e testar |
| 6 | Frontend React + Tailwind: galeria de fotos e status do dispositivo | Ver as fotos no navegador |
| 7 | Extras: atualização em tempo real (WebSocket/SSE), token simples de segurança | Foto aparece sozinha na tela |

---

## Stack final

- **Dispositivo:** ESP32-CAM (Arduino/C++), comunicando por HTTP
- **Backend:** Python + **FastAPI** + Uvicorn (gera a documentação automática em `/docs`)
- **Armazenamento:** pasta `photos/` no PC (sem banco de dados por enquanto)
- **Frontend:** TypeScript + React + Tailwind, usando `fetch` na mesma API

---

## Glossário rápido

- **HTTP POST:** "enviar algo" ao servidor (aqui, a foto).
- **HTTP GET:** "pedir algo" ao servidor (aqui, a lista de fotos).
- **API / rota:** endereço do servidor que faz uma tarefa (`/api/photos`).
- **Heartbeat:** mensagem periódica dizendo "estou ligado".
- **Cooldown:** tempo de espera mínimo entre duas fotos.
