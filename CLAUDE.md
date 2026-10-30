# CLAUDE.md

## Objetivo do projeto

A ESP32-CAM detecta movimento com o sensor PIR, tira uma foto e envia por **HTTP** para um servidor Python (FastAPI), que salva a imagem em `photos/`. Depois, um frontend em React + TypeScript + Tailwind vai consumir a mesma API para listar, ver e apagar as fotos.

O objetivo final está em `docs/objetivo.md`: monitorar o quarto e identificar quem tentou usar o PC. O reconhecimento facial (OpenCV) **só entra depois do protótipo pronto e quando o usuário autorizar**.

## Stack e estrutura (ver `PLANO.md`)

- **Dispositivo:** ESP32-CAM (Arduino/C++), usando `esp_camera.h`, `WiFi.h` e `HTTPClient.h`
- **Backend:** Python + FastAPI + Uvicorn
- **Armazenamento:** pasta `photos/`, sem banco de dados por enquanto
- **Frontend (futuro):** React + TypeScript + Tailwind, com `fetch` na mesma API

```
firmware/people-detect/people-detect.ino   código da ESP32-CAM
server/main.py, server/requirements.txt    servidor FastAPI (a criar)
photos/                                    fotos salvas (criada pelo servidor)
web/                                       frontend React (futuro)
assets/                                    diagramas (fontes em assets/src/)
docs/, TCC_word/                           material do TCC (ver seção abaixo)
PLANO.md                                   plano e etapas
```

## Comunicação

Só HTTP, **sem MQTT**. As rotas estão em `PLANO.md`: `POST /api/photos`, `POST /api/heartbeat`, `GET /api/photos`, `GET /photos/<nome>.jpg` e `GET /api/device`.

## Hardware atual

- **Placa:** ESP32-CAM AI-Thinker
- **PIR HC-SR501:** OUT no GPIO 13, VCC no 5V e GND no GND
- **LED + resistor de 220 Ω:** hoje está no **GPIO 16**, mas precisa ir para o **GPIO 14**, porque o GPIO 16 conflita com a PSRAM da câmera
- **Alimentação:** regulador de tensão com saída de 5 V, que precisa aguentar pelo menos 1 A (câmera + Wi-Fi)
- **Protoboard:** 30 linhas, com os trilhos na ordem `+ − | a–e | f–j | + −`

### Cores dos fios

| Ligação | Cor |
|---|---|
| Regulador OUT+ | roxo |
| Regulador OUT− | branco |
| PIR VCC | vermelho |
| PIR OUT | roxo |
| PIR GND | preto |
| GPIO do LED | verde |

## Diagramas

- `assets/circuito.png` é gerado com:
  `rsvg-convert -b white -o assets/circuito.png assets/src/circuito.svg`
- `assets/circuito-protoboard.png` é gerado com:
  `python3 assets/src/circuito-protoboard.py | rsvg-convert -b white -o assets/circuito-protoboard.png`
- Se o hardware mudar (pino, fio ou componente), atualize as fontes em `assets/src/` **e** gere os PNGs de novo.

## Etapa atual do PLANO.md

**Etapa 1: testar a câmera sozinha com o exemplo CameraWebServer.** A etapa 0 (branch `new-plan` e arquivos movidos) está concluída.

> Mantenha esta seção atualizada. Quando uma etapa for testada e confirmada pelo usuário, troque para a próxima e anote em uma linha o que foi validado.

## TCC: tarefa importante, mas só no final

`docs/` e `TCC_word/` vieram da branch antiga e **ainda descrevem o projeto antigo** (Telegram, app Kotlin, servidor de faces etc.).

- **Não editar agora.** Só atualizar quando o projeto estiver funcionando 100% conforme `docs/objetivo.md`.
- Nesse momento, revisar `docs/NOTE.md`, as imagens em `docs/images/` e `TCC_word/TCC_ALVES_EtapaFinal_2026.docx` para refletir o projeto novo.
- Lembrar o usuário dessa tarefa quando o protótipo estiver completo.

## Como trabalhar comigo

- Sou dev júnior em IoT e quero aprender.
- Responda em português, de forma didática e pontual, sem textão.
- Prefira me dar o passo a passo e explicar o porquê, para que eu mesmo faça. **Só escreva ou altere código quando eu pedir.**
- Uma etapa de cada vez, sempre dizendo **como testar** se deu certo.
- Antes de mexer em pinos ou na ligação do circuito, avise sobre riscos de hardware: alimentação, pinos de boot (GPIO 0, 2, 12 e 15) e PSRAM (GPIO 16).
