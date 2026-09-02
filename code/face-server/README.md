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
tem **exatamente um rosto** e já indexa cada amostra nova (sem reprocessar as
demais pessoas — ver §4.2).

Pela linha de comando:

```bash
curl -X POST http://localhost:8000/people -F "name=João Victor" -F "category=Morador" -F "rooms=<id-do-comodo>,<outro-id>" -F "files=@foto1.jpg" -F "files=@foto2.jpg" -F "files=@foto3.jpg"
```

Uma pessoa fica com `status = "ativo"` a partir de `MIN_SAMPLES_ACTIVE`
amostras (padrão 3); abaixo disso fica `"pendente"`.

### As fotos precisam mostrar o rosto de frente

O detector HOG do dlib só encontra rosto **aproximadamente de frente**. Foto de
perfil, de queixo erguido ou de cabeça baixa volta com `0 rosto(s) encontrados`
e a amostra é recusada — medido com 5 fotos da mesma pessoa: as 2 frontais (uma
delas com o rosto ¾ virado) entraram, e as 3 de ângulo forte foram recusadas em
qualquer resolução e em qualquer rotação da imagem. Varie **expressão, luz e
distância**, não o ângulo da cabeça.

O detector `cnn` do dlib acha esses rostos de ângulo, mas leva **166 s por foto**
nesta CPU (o HOG leva 1,6 s), então não é usado — ficaria muito acima do timeout
do app.

`POST /people` e `POST /people/{id}/samples` respondem com o que **não** entrou:

```json
{
  "person": { "...": "...", "samples": 2 },
  "warnings": ["foto3.jpg: 0 rosto(s) encontrados (esperado exatamente 1)"],
  "refused": [{ "file": "foto3.jpg", "faces": 0, "reason": "0 rosto(s) encontrados (esperado exatamente 1)" }]
}
```

`refused` é o campo que o app usa para apontar qual foto caiu e por quê;
`warnings` traz a mesma coisa em texto corrido e continua existindo para não
quebrar quem já o lia.

Se você copiar imagens direto para `known_faces/<id>/`, recarregue com:

```bash
curl -X POST http://localhost:8000/reload
```

---

## 4. Contrato da API

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | `{status, known_people, known_samples, uptime_s, engine_available, tolerance, auto_unlock, room, rooms, presence, liveness, assistant}` |
| `POST` | `/reload` | Recarrega os *encodings* de `known_faces/` |
| `POST` | `/detect` | Multipart `file` (JPEG) + `source` + `rotation?` + `room?` + `room_id?`. Usado pelo ESP32 no evento do PIR |
| `POST` | `/scan` | JSON `{esp32_url?, rotation?, room?, room_id?}` — puxa `/capture` do ESP32 e roda o mesmo pipeline |
| `GET` | `/detections?limit&offset` | Histórico paginado, mais recente primeiro |
| `GET` | `/media/detections/<arquivo>` | Imagem da detecção (arquivos estáticos) |
| `GET` | `/categories` | `{categories: ["Morador", "Visitante", "Prestador"]}` — chips da tela de cadastro |
| `GET` | `/people` | `{people: [{id, name, category, rooms, room_names, authorized, samples, status, created_at, access_label}]}` |
| `POST` | `/people` | Multipart `name`, `category`, `rooms` (ids separados por vírgula), `authorized`, `files` (3–5) |
| `PATCH` | `/people/{id}` | JSON com `name`/`category`/`rooms`/`authorized` |
| `DELETE` | `/people/{id}` | Remove metadados e amostras |
| `GET` | `/people/{id}/samples/{n}` | n-ésima amostra (JPEG, `n` começa em 1) |
| `POST` | `/people/{id}/samples` | Adiciona amostras a uma pessoa existente |
| `POST` | `/enroll/from-esp32` | JSON `{person_id, esp32_url?, rotation?}` — captura no ESP32 e usa como amostra |
| `GET`/`POST` | `/rooms` | Lista e cria cômodos |
| `PATCH`/`DELETE` | `/rooms/{id}` | Renomeia e remove (sai do cadastro das pessoas que o tinham) |
| `POST` | `/assistant/ask` | Pergunta em português → consulta respondida ou **proposta** |
| `POST` | `/assistant/apply` | Aplica uma proposta confirmada pelo administrador |

> [!IMPORTANT]
> **`rotation`** (0/90/180/270) gira o quadro **antes** do reconhecimento e é
> gravado já girado em `detections/`. O detector do dlib não encontra rosto de
> cabeça para baixo, então uma câmera montada invertida precisa de `180` — sem
> isso todo scan devolve `no_face`. Omitido na requisição, vale o `ROTATION` do
> `.env` (é o caso do firmware no evento do PIR).

> [!IMPORTANT]
> **`room_id`** (ou **`room`** pelo nome) é o cômodo vigiado pela câmera. Com
> ele o servidor confronta os **cômodos liberados** da pessoa (§4.1) e devolve
> `access_allowed: false` para quem está no lugar errado — nesse caso
> `authorized` também vira `false` e o `AUTO_UNLOCK` não dispara. Omitido, vale
> o `ROOM` do `.env`. Sem cômodo algum, o acesso continua decidido só pela
> chave mestra `authorized`.

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
  "detection": {
    "id": "...", "time": 1787000042.5, "time_label": "29/08/2026 15:34:02",
    "category": "Morador", "source": "pir",
    "room": "Quarto do João", "access_allowed": true, "access_reason": "Quarto do João consta nos cômodos liberados"
  }
}
```

`status` ∈ `recognized` | `unknown` | `no_face`.
`access_allowed` ∈ `true` | `false` | `null` (sem regra ou sem cômodo para avaliar).

### 4.1 Cômodos e acesso por pessoa

O acesso é **declarado**, não interpretado. Duas peças:

| Peça | Papel |
|---|---|
| **Cômodo** (`/rooms`) | um lugar vigiado por uma câmera |
| **Pessoa** | tem a **lista de cômodos** que pode acessar + a chave mestra `authorized`; `category` (Morador/Visitante/Prestador) é o vínculo, usado só para leitura |

A decisão (`access_control.evaluate`) é uma tabela, nesta ordem:

```
1. pessoa com authorized = false   -> NEGADO  (chave mestra vence tudo)
2. cômodo não informado            -> SEM OPINIÃO (decide só o authorized)
3. cômodo marcado no cadastro      -> PERMITIDO
4. qualquer outro caso             -> NEGADO  (fecha por padrão)
```

Quem marca os cômodos é a tela **Cadastrar/Editar Pessoa** do app, cômodo por
cômodo (design `design-app-mobile/cadastrar-nova-pessoa.png`). Um cadastro sem
nenhum cômodo marcado não entra em lugar nenhum.

> [!IMPORTANT]
> **Nenhum modelo de IA participa desta decisão.** Uma versão anterior derivava
> a regra de uma observação em texto livre com um LLM local; a medição
> registrada em `tasks.md` §3.1 mostrou o problema — para *"Quarto do Antônio"*
> o modelo respondeu `acesso_total: true`, liberando a pessoa na casa inteira.
> Num controle de acesso físico a mesma entrada precisa dar sempre a mesma
> saída, e isso é uma tabela, não uma frase.

**Migração automática:** cadastros com `level_id` (do desenho anterior, com
níveis de acesso) viram categoria + cômodos na primeira leitura — o nome do
nível passa a ser a categoria e os cômodos do nível vão para a pessoa
(`all_rooms` vira a lista de cômodos existentes). Cadastros ainda mais antigos,
só com `category` em texto, mantêm a categoria e ficam **sem cômodo** — nunca
migrar para mais permissivo do que estava. Os campos legados (`level_id`,
`note`, `access_rule`) somem do JSON.

### 4.2 Tempo de resposta do cadastro

Cadastrar, editar e excluir pessoa demoravam dezenas de segundos. Eram duas
causas, as duas medidas nesta máquina:

| Causa | O que acontecia | Correção |
|---|---|---|
| Reprocessar tudo | `POST /people`, `POST /people/{id}/samples`, `/enroll` e `DELETE /people/{id}` chamavam `load_known_faces()`, que **recodifica todas as amostras de todas as pessoas** (~1 s a 7 s por imagem) | `face_engine.index_sample()` indexa só a amostra nova e `forget_person()` só remove a pessoa do índice |
| Foto em tamanho original | o detector HOG custa proporcional à área: uma foto de celular de 2544 px levava **7,5 s**; a mesma em 1024 px leva **1,6 s** e acha o mesmo rosto | `MAX_ANALYSIS_WIDTH` (padrão 1024) reduz o quadro antes de detectar; os frames do ESP32 (640×480) passam intactos |

Medido com 3 amostras de 2544 px: **cadastro 22,3 s → 5,9 s**, **exclusão
~0,2 s** (antes crescia com o banco inteiro), **edição (`PATCH`) ~0,2 s**.

### 4.3 Vivacidade, presença e assistente

| Módulo | O que faz | Padrão |
|---|---|---|
| `liveness.py` (G1) | barra foto impressa/tela mostrada à câmera | **desligado** — precisa de `liveness_model.json` calibrado |
| `presence.py` (G2) | distingue "quadro vazio" de "há alguém sem rosto legível" | ligado (`PRESENCE_FALLBACK`) |
| `assistant.py` (G3) | perguntas e comandos em português para o ADM | ligado com `OLLAMA_MODEL` |

**Vivacidade** falha *aberta* de propósito: sem modelo calibrado ela não opina,
porque um sistema de acesso não pode trancar todo mundo do lado de fora por
falta de um arquivo. Para calibrar:

```bash
# monte datasets/liveness/real/ e datasets/liveness/spoof/ (20+ imagens cada,
# capturadas com o PRÓPRIO ESP32-CAM)
.venv\Scripts\python.exe tools/liveness_calibrate.py
# só então: LIVENESS_ENABLED=true no .env
```

**Presença** usa as cascatas Haar e o detector HOG que já vêm com o OpenCV — sem
download e sem dependência nova. Só roda quando o reconhecimento não achou
rosto, então não pesa no caminho normal. Novo status: `person_no_face`.

**Assistente** traduz português em consulta ao histórico ou em **proposta** de
mudança. Ele nunca decide acesso e nunca destranca nada; toda escrita volta como
proposta e só é aplicada em `/assistant/apply`, depois da confirmação na tela.

---

## 5. Estrutura em disco

```
code/face-server/
├── main.py                 # rotas FastAPI
├── config.py               # leitura do .env
├── face_engine.py          # encodings + comparação (OpenCV / face_recognition)
├── people_store.py         # metadados das pessoas (um JSON por pessoa)
├── detections_store.py     # histórico de detecções (imagem + sidecar JSON)
├── access_control.py       # decisão de acesso (determinística, sem IA)
├── rooms_store.py          # cômodos
├── liveness.py             # anti-spoofing (G1, desligado sem modelo)
├── presence.py             # detecção de presença (G2)
├── assistant.py            # assistente do ADM em português (G3)
├── esp32_client.py         # /capture e /api/unlock no ESP32
├── tools/                  # experimentos e calibração (material de TCC)
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
**97 testes.** Cobrem `/detect` (rosto conhecido, desconhecido, sem rosto e
presença sem rosto), o CRUD de pessoas e cômodos, a edição do cadastro, a decisão de acesso,
a migração dos cadastros antigos, `/scan`, `/enroll/from-esp32`, o histórico,
a vivacidade e o assistente. Motor facial e Ollama entram por dublês e a
integração fica desligada por padrão no `conftest.py`, então a suíte roda
offline e não depende do `.env` da máquina.

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
