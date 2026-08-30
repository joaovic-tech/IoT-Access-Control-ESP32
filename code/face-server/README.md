# face-server — Reconhecimento facial (FastAPI + OpenCV)

Servidor Python que roda num PC da LAN. Recebe imagens do **ESP32-CAM**
(disparo do PIR ou botão "Escanear ambiente" do app), identifica o rosto contra
as amostras cadastradas e guarda o histórico de detecções que o app exibe.

O **vídeo ao vivo não passa por aqui** — vai direto do ESP32 para o app
(`http://<ip-do-esp32>:81/stream`).

---

## 1. Instalação

```bash
cd code/face-server
python -m venv .venv
```

Ative o ambiente virtual:

```bash
.venv\Scripts\activate
```

```bash
pip install -r requirements.txt
```

> **dlib / face_recognition no Windows:** exigem o *Build Tools for Visual
> Studio* (workload "Desenvolvimento para desktop com C++"). Sem eles o
> servidor ainda sobe, mas em modo degradado: `/health` responde
> `engine_available: false` e as rotas de reconhecimento retornam **503**.
> O CRUD de pessoas e o histórico continuam funcionando.

Configuração:

```bash
copy .env.example .env
```

Ajuste pelo menos `ESP32_DEFAULT_URL` com o IP real da câmera.

---

## 2. Execução

```bash
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Documentação interativa (Swagger): <http://localhost:8000/docs>.

Teste rápido:

```bash
curl http://localhost:8000/health
```

---

## 3. Cadastro de rostos

O caminho recomendado é pelo app (tela **Cadastrar Pessoa**), que chama
`POST /people`. O servidor cria `known_faces/<id>/`, valida que cada amostra
tem **exatamente um rosto** e recarrega os *encodings* sozinho.

Pela linha de comando:

```bash
curl -X POST http://localhost:8000/people -F "name=João Victor" -F "category=Morador" -F "authorized=true" -F "files=@foto1.jpg" -F "files=@foto2.jpg" -F "files=@foto3.jpg"
```

Uma pessoa fica com `status = "ativo"` a partir de `MIN_SAMPLES_ACTIVE`
amostras (padrão 3); abaixo disso fica `"pendente"`.

Se você copiar imagens direto para `known_faces/<id>/`, recarregue com:

```bash
curl -X POST http://localhost:8000/reload
```

---

## 4. Contrato da API

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | `{status, known_people, known_samples, uptime_s, engine_available, tolerance, auto_unlock}` |
| `POST` | `/reload` | Recarrega os *encodings* de `known_faces/` |
| `POST` | `/detect` | Multipart `file` (JPEG) + `source` + `rotation?`. Usado pelo ESP32 no evento do PIR |
| `POST` | `/scan` | JSON `{esp32_url?, rotation?}` — puxa `/capture` do ESP32 e roda o mesmo pipeline |
| `GET` | `/detections?limit&offset` | Histórico paginado, mais recente primeiro |
| `GET` | `/media/detections/<arquivo>` | Imagem da detecção (arquivos estáticos) |
| `GET` | `/people` | `{people: [{id, name, category, authorized, samples, status, created_at, note}]}` |
| `POST` | `/people` | Multipart `name`, `category`, `authorized`, `note`, `files` (3–5) |
| `PATCH` | `/people/{id}` | JSON com `name`/`category`/`authorized`/`note` |
| `DELETE` | `/people/{id}` | Remove metadados e amostras |
| `GET` | `/people/{id}/samples/{n}` | n-ésima amostra (JPEG, `n` começa em 1) |
| `POST` | `/people/{id}/samples` | Adiciona amostras a uma pessoa existente |
| `POST` | `/enroll/from-esp32` | JSON `{person_id, esp32_url?, rotation?}` — captura no ESP32 e usa como amostra |

> [!IMPORTANT]
> **`rotation`** (0/90/180/270) gira o quadro **antes** do reconhecimento e é
> gravado já girado em `detections/`. O detector do dlib não encontra rosto de
> cabeça para baixo, então uma câmera montada invertida precisa de `180` — sem
> isso todo scan devolve `no_face`. Omitido na requisição, vale o `ROTATION` do
> `.env` (é o caso do firmware no evento do PIR).

Resposta de `/detect` e `/scan`:

```json
{
  "status": "recognized",
  "detected": [
    { "person_id": "6f2a1c9b4e70", "name": "João Victor", "authorized": true, "distance": 0.31, "box": [10, 90, 90, 10] }
  ],
  "file": "20260829_153402_recognized_joao-victor.jpg",
  "url": "/media/detections/20260829_153402_recognized_joao-victor.jpg",
  "unlocked": false,
  "detection": { "id": "...", "time": 1787000042.5, "time_label": "29/08/2026 15:34:02", "category": "Morador", "source": "pir" }
}
```

`status` ∈ `recognized` | `unknown` | `no_face`.

Exemplos com `curl`:

```bash
curl -X POST http://localhost:8000/detect -F "file=@frame.jpg" -F "source=pir"
```

```bash
curl -X POST http://localhost:8000/scan -H "Content-Type: application/json" -d "{\"esp32_url\":\"http://192.168.15.72\"}"
```

---

## 5. Estrutura em disco

```
code/face-server/
├── main.py                 # rotas FastAPI
├── config.py               # leitura do .env
├── face_engine.py          # encodings + comparação (OpenCV / face_recognition)
├── people_store.py         # metadados das pessoas (um JSON por pessoa)
├── detections_store.py     # histórico de detecções (imagem + sidecar JSON)
├── esp32_client.py         # /capture e /api/unlock no ESP32
├── notifier.py             # Telegram opcional (desligado por padrão)
├── known_faces/<id>/*.jpg  # amostras faciais        (não versionado)
├── people/<id>.json        # metadados               (não versionado)
├── detections/             # histórico               (não versionado)
└── tests/                  # pytest
```

---

## 6. Testes

```bash
.venv\Scripts\python.exe -m pytest tests -q
```

Os testes substituem o motor facial por um dublê, então rodam sem `dlib`
instalado — bastam `fastapi`, `httpx`, `python-multipart` e `pytest`.
Cobrem `/detect` com rosto conhecido, desconhecido e sem rosto, o CRUD de
pessoas, `/scan`, `/enroll/from-esp32` e o histórico de detecções.

---

## 7. Segurança

- Nenhum segredo no código: tudo vem do `.env` (ignorado pelo git).
- `known_faces/`, `people/` e `detections/` guardam **dado biométrico pessoal**
  e não são versionados.
- Toda detecção gera uma linha `[ACESSO] status=... autorizado=... origem=...`
  no log, além do arquivo em `detections/` — sucesso e falha ficam registrados.
- `AUTO_UNLOCK` vem **desligado**. Só ligue depois de validar o
  reconhecimento, porque ele aciona o relé físico da porta.
- Se o firmware definir `ACCESS_TOKEN`, repita o valor em `ESP32_ACCESS_TOKEN`
  para que o servidor consiga chamar `/api/unlock`.
