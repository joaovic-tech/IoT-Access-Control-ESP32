# Tarefas e diagnóstico do projeto

Revisão de código realizada em 12/09/2026. Este documento organiza os requisitos do autor, o comportamento encontrado no firmware, servidor e aplicativo, as pendências e as melhorias propostas.

A premissa é monitorar ambientes com ESP32-CAM e PIR, identificar pessoas no servidor e permitir que o usuário consulte e acompanhe os eventos por um chat com IA, sem precisar inspecionar cada fotografia. O provedor ainda será escolhido entre OpenAI, Anthropic (Claude) e Ollama, considerando qualidade em português, custo e capacidade do computador disponível.

**Como interpretar o progresso:** “implementado” significa que existe um caminho no código; não comprova funcionamento na placa, no celular ou com um modelo real. As funcionalidades básicas de reconhecimento e cadastros foram marcadas como concluídas pelo autor no texto anterior. O autor informou que o PIR ainda não foi testado. Nesta revisão não foram executados builds, testes, chamadas pagas, gravação de firmware ou ensaios físicos. Não há base medida para atribuir um percentual geral de conclusão.

## 1. Requisitos do autor e situação atual

| Requisito original | Situação | O que ainda precisa ser feito |
|---|---|---|
| Reconhecer cadastrados e distinguir desconhecidos | Implementado; informado como concluído pelo autor | Medir erros em capturas independentes e diferentes condições |
| Criar, editar e excluir pessoas | Implementado; informado como concluído pelo autor | Revalidar o fluxo integrado e esclarecer o significado de cadastro pendente |
| Criar cômodos e informar onde fica cada dispositivo | Parcial na integração completa | O app salva a associação, mas o evento automático do PIR não transmite a identidade/configuração do dispositivo |
| Criar, editar e excluir dispositivos | Implementado no app; informado como concluído pelo autor | Sincronizar identidade/configuração com o servidor para eventos automáticos e múltiplas câmeras |
| Chat com contexto de pessoas, horários, cômodos e fotos | Parcial | Existe consulta via Ollama na tela de cômodos, sem conversa completa, filtro temporal ou apresentação das evidências retornadas |
| Capturar e armazenar imagem quando o PIR detectar movimento | Implementado em código; ensaio físico pendente | Validar ligação, disparo, captura, envio, armazenamento e falhas |
| Evitar repetição enquanto houver movimento contínuo | Parcial | Há borda de subida e intervalo de 10 s; falta agrupar episódios e definir o rearme |
| Notificar o celular após o evento | Pendente no Android | Atualização periódica do histórico e Telegram opcional não substituem notificação do aplicativo |
| Mostrar pelo celular se o PIR detecta movimento | Parcial | API e modelos Android recebem os campos, mas a interface não os apresenta |
| Testar o conjunto e reformular o artigo com dados reais | Pendente | Produzir evidências reproduzíveis e corrigir afirmações sem comprovação |

## 2. O que o projeto faz hoje

### 2.1 Dispositivo ESP32-CAM

Fontes principais: `code/CameraWebServer/CameraWebServer.ino`, `face_client.cpp`, `app_httpd.cpp` e `iot_access.h`.

- Captura JPEG e oferece vídeo MJPEG em `/stream`, porta 81. A API de controle e `/capture` ficam na porta 80. O vídeo vai diretamente ao app.
- Configura inicialmente VGA, qualidade JPEG 12 e dois buffers em PSRAM. Há tentativa de configuração reduzida sem PSRAM, mas uma chamada posterior força VGA novamente; esse modo precisa de correção e validação.
- Lê o PIR no GPIO 13, aplica debounce de 50 ms e conta transições de baixo para alto. O contador representa disparos elétricos, não pessoas nem imagens salvas.
- Na borda de subida, respeita intervalo de 10 segundos desde o último enfileiramento aceito e solicita a uma tarefa FreeRTOS a captura de um JPEG e o envio por `POST /detect`.
- Envia apenas `source` e `file`. Não envia `device_id`, `event_id`, horário de captura, cômodo ou orientação. O destino do servidor é definido na configuração compilada.
- Usa timeout de conexão/leitura de 5 segundos e recusa outro pedido enquanto há trabalho pendente ou em andamento. Não possui fila persistente nem recuperação automática da fotografia após falha de envio.
- Expõe em `/api/status` nível do PIR, contador, estado de envio, último status HTTP, memória e Wi-Fi. Status HTTP não confirma recebimento de notificação pelo celular.
- Controla LED flash e relé no GPIO 14, com pulso temporizado de destravamento, normalmente de 3 segundos. Código de atuação não comprova acionamento de uma fechadura real.
- Mantém Wi-Fi ativo e tenta reconectar periodicamente. Não implementa deep sleep.
- **Não grava no microSD de 2 GB.** O histórico fica no computador servidor. GPIOs 13 e 14 também participam da interface SD_MMC; uso futuro do cartão exige revisar pinagem e modo de operação.

### 2.2 Servidor Python

Fontes principais: `code/face-server/main.py`, `face_engine.py`, `access_control.py`, `people_store.py`, `rooms_store.py`, `detections_store.py`, `config.py`, `presence.py`, `liveness.py` e `notifier.py`.

- Usa FastAPI, OpenCV e `face_recognition`/Dlib. Recebe imagens em `/detect` ou busca uma captura do ESP32 em `/scan`.
- Aplica rotação e redução de escala, detecta rostos e compara vetores faciais com as amostras cadastradas. O limiar padrão em código é 0,60; isso não demonstra adequação ao ambiente.
- Distingue `recognized`, `unknown`, `person_no_face` e `no_face`. Detectores auxiliares tentam identificar presença quando não há rosto reconhecível.
- Gerencia pessoas, fotografias, categorias, autorização geral e cômodos permitidos. Cadastros com poucas amostras aparecem como pendentes, mas já podem participar do reconhecimento.
- Gerencia criação, renomeação e exclusão de cômodos. Excluir um cômodo remove a permissão correspondente das pessoas.
- Calcula autorização por regras: pessoa bloqueada é negada; com cômodo resolvido, verifica a permissão. Sem cômodo resolvido, a autorização geral pode prevalecer. Não é negação obrigatória em toda ausência de informação.
- A resposta da detecção contém os rostos encontrados, mas o registro persistido guarda uma pessoa principal: o rosto reconhecido de menor distância. **O histórico não preserva todas as identidades de cada imagem**, limitando consultas sobre grupos.
- Salva JPEG e JSON com horário, pessoa, cômodo, origem, classificação, autorização e distância. O horário é gerado na persistência, não representa medição do instante exato do movimento.
- **Mantém até 500 detecções válidas**, removendo as mais antigas a cada gravação. Não é histórico ilimitado nem retenção por dias. Consultas precisam informar o período disponível.
- Possui vivacidade opcional, dependente de modelo calibrado. Vem desabilitada por padrão; ausência de resultado não impede autorização.
- Possui destravamento automático opcional, desabilitado por padrão. Quando habilitado, o comando ocorre antes de salvar o registro, deixando lacuna de auditoria em falhas de persistência.
- Envia opcionalmente fotos ao Telegram para eventos diferentes de `no_face`. O envio é síncrono no processamento e não constitui push Android.
- As rotas administrativas e de imagens não possuem autenticação de usuário implementada. A comunicação atual entre os componentes locais usa HTTP.

### 2.3 Aplicativo Android

Fontes principais: `code/ESPCamApp/app/src/main/java/com/example/espcamapp/views/` e pacotes `data/model`, `data/remote`, `data/repo` e `data/settings`.

- Possui abas Principal, Dispositivos e Pessoas, além de cadastro de pessoa e gerenciamento de cômodos.
- Exibe vídeo ao vivo, miniaturas de detecções e resultado do comando manual “Escanear ambiente”.
- Consulta as 12 detecções recentes a cada 5 segundos enquanto o respectivo ViewModel permanece ativo. A consulta não filtra pela câmera selecionada; o carrossel não é histórico exclusivo dela.
- Cadastra, edita e exclui dispositivos, salvando endereço, portas, orientação e cômodo no DataStore local. Isso não provisiona Wi-Fi nem atualiza a configuração compilada no firmware.
- No scan manual, envia URL do dispositivo, rotação e cômodo ao servidor. Esse comportamento não se transfere automaticamente ao caminho PIR.
- Gerencia pessoas, amostras da galeria ou da câmera, categoria, autorização e cômodos permitidos. Exibe indicação de amostras faltantes.
- Mostra disponibilidade dos dispositivos, controla iluminação e solicita destravamento com confirmação visual.
- Consulta status dos dispositivos a cada 8 segundos. Os DTOs incluem `pir` e `pir_count`, sem indicador na tela; `detect_busy` e `detect_last_status` ainda precisam ser mapeados para apresentação.
- **Não possui notificações Android, serviço de recebimento push ou conversa persistente.** Não foi encontrado canal de notificações nem permissão correspondente no manifesto.
- As chamadas diretas aos atuadores não acrescentam o token opcional esperado pelo firmware; habilitar essa proteção exige compatibilizar o cliente.

### 2.4 Assistente existente

Fontes principais: `code/face-server/assistant.py`, rotas `/assistant/ask` e `/assistant/apply`, `AccessScreen.kt`, `AccessViewModel.kt` e `data/repo/AccessRepository.kt`.

- Existe uma base reaproveitável: envia a pergunta ao Ollama em `/api/generate`, solicitando JSON com intenção e parâmetros. A ativação depende de `OLLAMA_MODEL`; não foi verificado um modelo em execução.
- O contexto contém nomes de pessoas, categorias e cômodos. **Não contém fotos, histórico completo ou mensagens anteriores.**
- Permite consultar detecções por pessoa, cômodo, status e autorização, ou propor mudança de categoria/liberação de cômodo. Não atende livremente qualquer tarefa de monitoramento.
- Carrega apenas as últimas 200 detecções antes de filtrar e devolve normalmente até 20. Não implementa datas, contagem de pessoas únicas ou agregações por horário.
- A interface sugere “quem entrou na cozinha ontem?”, mas **“ontem” não é aplicado como filtro**. O resultado pode incluir outros dias.
- O servidor responde com contagem padronizada e registros, sem segunda etapa de explicação contextual pelo modelo.
- O repositório Android reduz os registros a uma contagem. A tela não mostra fotos, nomes e horários desses resultados e mantém apenas a pergunta/resposta atual.
- Alterações geram proposta com confirmação visual, mas `/assistant/apply` aceita a proposta diretamente, sem sessão autenticada nem confirmação vinculada e verificada no servidor.
- **Não há integração com OpenAI ou Anthropic, seleção de provedor, gestão de chaves ou medição de custo de IA.**

## 3. O que falta para entregar a proposta

Os itens desta seção permanecem abertos até implementação e verificação. Os critérios são um plano de aceite; esta revisão não criou novos testes automatizados.

### 3.1 PIR, evento e notificação

- [ ] **PIR-01 — Validar o sensor físico.** Conferir alimentação, GPIO 13, estabilização, sensibilidade e modo de disparo. Registrar sinal, contador, captura e imagem salva. Aceite: movimento controlado produz evento rastreável; ausência de disparo tem diagnóstico identificável.
- [ ] **PIR-02 — Agrupar um episódio de movimento.** Implementar estabilização, estado armado, início do evento e espera de rearme. Capturar no início e rearmar após intervalo configurável sem movimento. O cooldown atual permite novos envios após 10 s com novas bordas e pode ignorar um disparo durante o intervalo sem recuperá-lo. Aceite: pulsos a cada 2 s no mesmo episódio não geram novas fotos/notificações/chamadas de IA; após rearme, novo episódio gera evento.
- [ ] **PIR-03 — Mostrar diagnóstico no app.** Apresentar movimento, ausência de movimento recente, espera de rearme e dispositivo sem comunicação; incluir último disparo, contador e resultado do envio. Aceite: polling de 8 s não apaga a evidência de pulsos curtos; estado antigo não aparece como atual após desconexão.
- [ ] **EVT-01 — Identificar câmera e evento.** Adotar ID estável de dispositivo e ID único de evento, preservado nos reenvios; manter associação com cômodo, orientação e destino no servidor. Aceite: alterar cômodo no app afeta o PIR correto, inclusive com duas câmeras.
- [ ] **EVT-02 — Tratar entrega e falhas.** Definir fila limitada, timeout compatível, tentativas limitadas e confirmação de persistência. Reenvios não duplicam registros ou alertas. Hoje o ESP32 pode desistir enquanto o servidor ainda processa. Sem armazenamento durável, declarar o que pode se perder em falhas e reinícios.
- [ ] **NOT-01 — Notificar pelo Android.** Após salvar o evento, emitir alerta com cômodo, horário, identificação/classificação disponível e abertura da captura. Implementar canal, permissão quando exigida, preferências e deduplicação por evento.
- [ ] **NOT-02 — Definir segundo plano e acesso fora da LAN.** Escolher entrega compatível com o cenário: conexão local com serviço visível ou push com infraestrutura apropriada. Polling em ViewModel não garante entrega com app fechado. Aceite: documentar/verificar primeiro plano, segundo plano, tela bloqueada, perda/retorno da rede; separar fechamento normal de parada forçada pelo usuário.

O PIR detecta movimento, não identidade, permanência imóvel nem direção de passagem. Rearme após silêncio é uma heurística, não comprova saída. Uma segunda pessoa pode chegar no mesmo episódio e não gerar nova foto. Avaliar essa limitação antes de prometer registro de todos os acessos.

### 3.2 Chat e consultas com IA

**Princípio de arquitetura:** a visão computacional identifica padrões; o assistente interpreta perguntas e explica evidências; o servidor executa consultas e valida ações. Autorização física continua em regras explícitas. O chat de monitoramento deve funcionar mesmo sem ferramentas de alteração de permissões.

- [ ] **CHAT-01 — Criar tela de conversa.** Adicionar destino próprio, mensagens de usuário/assistente, carregamento, erro, cancelamento, nova conversa e histórico restaurável com limite definido. Reaproveitar cliente e rotas existentes quando adequado.
- [ ] **CHAT-02 — Consultar por período.** Validar filtros de pessoa/ID, cômodo/ID, câmera, status, autorização, início e fim. Resolver “hoje”, “ontem” e “à noite” com fuso e período explícitos; pedir esclarecimento quando necessário. Consultar todo o histórico retido, informando cobertura e paginação, sem corte silencioso das últimas 200 entradas.
- [ ] **CHAT-03 — Responder com evidências.** Mostrar resumo, total de eventos, pessoas distintas, horários e fotos recuperadas por ID. Preservar registros no modelo Android em vez de reduzi-los a contagem. Aceite: cada afirmação sobre evento tem registro verificável.
- [ ] **CHAT-04 — Manter contexto.** Entender “e no quarto?” após consulta à cozinha, preservando o período anterior. Guardar filtros e resumo da conversa dentro do orçamento; não reenviar todas as mensagens e fotografias indefinidamente.
- [ ] **CHAT-05 — Ampliar ferramentas.** Oferecer funções delimitadas para listar eventos, contar por pessoa/cômodo, consultar último registro, obter foto e verificar estado do dispositivo. Validar argumentos, permissões, limites e IDs no servidor. Não executar SQL livre, código ou URLs arbitrárias do modelo.
- [ ] **CHAT-06 — Explicitar ausência de evidência.** Distinguir ausência de registros, retenção vencida, câmera offline e informação insuficiente. Não transformar detecção em prova de entrada/saída nem ausência de foto em prova de ausência de pessoas.
- [ ] **CHAT-07 — Monitoramento solicitado no chat.** Converter “avise quando houver desconhecido na cozinha” em regra persistente com critérios, estado ativo e cancelamento. Avaliar eventos por código, sem consultar o modelo continuamente. Esse mecanismo ainda não existe.
- [ ] **CHAT-08 — Delimitar perguntas livres.** O autor deseja perguntas gerais. Dúvidas gerais podem receber explicação, mas afirmações sobre a instalação exigem consulta aos dados reais. Não prometer saber qualquer coisa ou acessar informação não registrada.
- [ ] **CHAT-09 — Proteger escritas, se mantidas.** Autenticar administrador e vincular proposta, parâmetros, usuário, validade e confirmação no servidor; impedir alteração/reexecução indevida. A confirmação visual atual é insuficiente. Manter destravamento físico fora das ferramentas do chat no primeiro escopo.

### 3.3 Provedor e orçamento de IA

- [ ] **IA-01 — Adaptar provedores no servidor.** Configurar provedor, modelo, URL permitida e credencial, começando por uma opção completa. Preservar contrato comum de ferramentas/respostas; diferenças de OpenAI, Anthropic e Ollama ficam no servidor, sem reescrever o chat.
- [ ] **IA-02 — Proteger credenciais.** Guardar chaves no ambiente/configuração protegida do servidor, nunca no APK, firmware ou Git. Cadastro de chave pela interface, se oferecido, exige endpoint autenticado, transporte protegido e mascaramento; não expor em logs/respostas. Chave do provedor não substitui autenticação do usuário.
- [ ] **IA-03 — Limitar consumo.** Definir limite de saída, contexto, ferramentas por turno, concorrência, tentativas e gasto mensal. Registrar modelo, uso e custo estimado por conversa; bloquear nova chamada ao atingir teto local, mantendo consulta convencional disponível.
- [ ] **IA-04 — Comparar candidatos.** Avaliar português, acerto de filtros, ferramentas, evidências, latência e custo por pergunta correta. Para Ollama local, registrar RAM/VRAM, CPU/GPU e impacto no reconhecimento. Escolher após medir, sem declarar vencedor antecipado.

### 3.4 Integridade e confiabilidade dos dados

- [ ] **DADOS-01 — Preservar o que a consulta promete.** Registrar todas as faces/identidades do evento se o produto responder sobre grupos, mantendo foto e resultados para revisão. Separar reconhecimento, autorização calculada e confirmação física de abertura.
- [ ] **DADOS-02 — Padronizar horários e retenção.** Separar captura, recebimento e processamento; definir relógio/fuso e sinalizar falta de sincronismo. Configurar retenção por período/capacidade e informar alcance do histórico. Não remover arquivos fora da política autorizada.
- [ ] **SEG-01 — Autenticar APIs e imagens.** Proteger cadastros, consultas, propostas, eventos e comandos; limitar câmeras/endpoints aceitos. Compatibilizar clientes com proteção dos atuadores e evitar segredos em URLs/logs.
- [ ] **SEG-02 — Explicitar falhas de autorização.** Definir comportamento restritivo para cômodo inválido/ausente, cadastro insuficiente e vivacidade indisponível quando houver atuação física. Registrar decisão antes da atuação e resultado depois; HTTP do relé não comprova movimento da fechadura.

## 4. O que melhorar ou alterar

### 4.1 Arquitetura recomendada para menor custo

Fluxo proposto, ainda não implementado por completo:

```text
PIR -> evento identificado -> JPEG -> servidor local
                                  -> reconhecimento e registro estruturado
                                  -> regra de alerta -> notificação Android

Chat Android -> servidor -> modelo interpreta pergunta
                         -> consulta validada aos registros
                         -> resumo e evidências -> Chat Android
```

Para “quem foi detectado na cozinha ontem?”, consultar metadados de reconhecimento, horário e cômodo deve bastar. A foto fica disponível como evidência. O modelo não precisa reanalisar cada imagem nem receber todo o acervo por pergunta. Contagens e filtros são calculados pelo servidor; o modelo interpreta a linguagem, seleciona ferramentas e explica resultados.

Perguntas visuais que os metadados não respondem, como descrição de objetos numa captura, podem receber análise da imagem selecionada com modelo compatível numa extensão posterior. Isso tem custo e requisitos diferentes. Descrição visual não deve substituir identidade do mecanismo de reconhecimento nem inferir intenção da pessoa.

### 4.2 OpenAI, Claude ou Ollama

| Opção | Aplicação | Custo e decisão pendente |
|---|---|---|
| OpenAI via API | Modelo remoto para interpretar perguntas e usar ferramentas do servidor | Medir tokens/chamadas e selecionar modelo econômico que cumpra os casos de aceite |
| Anthropic/Claude via API | Alternativa remota com ferramentas definidas pela aplicação | Comparar nas mesmas perguntas e limites; integração ainda não existe |
| Ollama com modelo realmente local | Inferência no computador, reaproveitando parte da integração | Sem tarifa de inferência de provedor remoto nesse modo; há consumo de hardware/energia e disputa de recursos com Dlib/OpenCV |

Recomendação: começar com consultas textuais e uma integração validada, deixando a troca configurável. Escolher o menor modelo que cumpra a qualidade exigida. Reduzir chamadas, tokens e tamanho de respostas segue a [orientação oficial de otimização de custos da OpenAI](https://developers.openai.com/api/docs/guides/cost-optimization).

As funções do projeto podem ser conectadas por [function calling da OpenAI](https://developers.openai.com/api/docs/guides/function-calling), [tool use do Claude](https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview) ou [tool calling do Ollama](https://docs.ollama.com/capabilities/tool-calling), conforme o modelo. Esses recursos não concedem acesso automático a arquivos e câmeras; é a aplicação que oferece as funções.

A API local do Ollama não exige chave por padrão; acesso direto ao serviço cloud exige autenticação. Configurar Ollama não garante execução local do modelo. Ver [autenticação do Ollama](https://docs.ollama.com/api/authentication). Fontes consultadas em 12/09/2026; preços e modelos específicos devem ser conferidos na escolha, não foram fixados neste diagnóstico.

Orçamento remoto: `custo = soma(tokens de entrada × tarifa de entrada + tokens de saída × tarifa de saída) / 1.000.000`, com tarifas por milhão e cobranças adicionais aplicáveis. Somar todas as chamadas do turno, tentativas e síntese após consulta. Sem volume mensal e medições, não há valor confiável em reais a declarar.

### 4.3 Melhorias posteriores ao fluxo principal

- [ ] Avaliar SQLite com índices por horário, câmera, pessoa e cômodo quando o histórico crescer. Busca correta no armazenamento atual pode atender ao primeiro escopo; não é necessário começar com banco vetorial.
- [ ] Separar processamento pesado e notificações do atendimento HTTP, com fila limitada e estado consultável. `/detect` é assíncrona, mas chama processamento síncrono; evitar atraso de outras requisições por visão computacional ou envio externo.
- [ ] Evitar sobreposição de consultas periódicas no app e reduzir polling fora da tela ativa, respeitando bateria, ciclo de vida e conectividade.
- [ ] Permitir mudar rede/servidor do dispositivo sem recompilar, com provisionamento e armazenamento protegidos.
- [ ] Corrigir configuração sem PSRAM e medir heap/PSRAM sob streaming e captura simultâneos.
- [ ] Avaliar microSD como fila limitada de fotos durante indisponibilidade do servidor. Gravação contínua de vídeo é requisito separado, a decidir; não é necessária para consulta por eventos.
- [ ] Melhorar alimentação, encapsulamento e fixação; medir consumo. Não declarar autonomia ou custo total sem ensaio e lista de materiais.
- [ ] Revisar retentativas dos comandos de relé no app: resposta perdida não deve causar novos pulsos sem controle de idempotência.
- [ ] Planejar acesso externo e múltiplos clientes de vídeo depois de estabilizar o uso local; não expor diretamente as APIs atuais como solução remota.

## 5. Validação e atualização do artigo

### 5.1 Evidências necessárias

- [ ] **VAL-01 — Registrar versão.** Anotar commit, ferramentas/dependências, modelo de IA, configuração sem segredos, computador, celular, firmware e condições do ambiente.
- [ ] **VAL-02 — Ensaiar a integração.** PIR -> captura -> câmera/cômodo -> persistência -> notificação -> evidência no app. Cobrir movimento repetido, rearme, falha de rede, servidor parado, reinício e duas câmeras.
- [ ] **VAL-03 — Avaliar reconhecimento.** Separar cadastro de avaliação, registrar identidade real, pessoas/capturas, iluminação, pose e distância; calcular falsas aceitações/rejeições com essas evidências.
- [ ] **VAL-04 — Avaliar chat.** Usar perguntas com resposta conferível: período vazio, histórico removido, nomes ambíguos, continuação de conversa, câmera offline e datas. Medir correção, latência e consumo; não chamar contagem de detecções de contagem de entradas físicas.
- [ ] **VAL-05 — Medir recursos.** Latência por etapa, autonomia, memória sob carga e custo do assistente; guardar método e valores brutos, sem reutilizar números antigos sem rastreabilidade.
- [ ] **VAL-06 — Verificar software.** Executar builds e testes existentes adequados às mudanças. Novos testes automatizados dependem da autorização prevista no AGENTS.md. Dublês de Ollama não comprovam qualidade do modelo real nem funcionamento físico do PIR.

### 5.2 Divergências e cuidados com o texto atual

- [ ] **ART-01 — Corrigir a situação do PIR.** A seção 3.7 afirma verificação pelo contador e histórico, mas o autor informa que o sensor ainda não foi testado. Usar “implementado, validação pendente” até produzir evidência.
- [ ] **ART-02 — Descrever o assistente entregue.** Hoje há integração simples com Ollama e formulário em cômodos. Não apresentar chat completo, memória, datas, contexto de fotos ou OpenAI/Claude como concluídos.
- [ ] **ART-03 — Corrigir a retenção.** O artigo afirma ausência de política automática, mas `detections_store.py` atualmente remove registros acima de 500. Explicar limite e efeito nas consultas.
- [ ] **ART-04 — Auditar números e datas.** Conferir medições antigas, conjunto da varredura de limiar e data da Tabela 3 (setembro no texto, outubro na fonte). Remover números sem origem verificável ou identificá-los adequadamente, sem inventar substitutos.
- [ ] **ART-05 — Delimitar conclusões.** Separar detecção, reconhecimento, autorização e atuação física. Não afirmar precisão operacional, latência, baixo custo total, autonomia ou segurança sem validação.
- [ ] **ART-06 — Atualizar arquitetura e limites.** Descrever notificação e chat finais, provedor/modelo usado, dados enviados externamente, retenção, limitações do PIR e alcance do histórico. Nuvem muda a alegação de processamento exclusivamente local.
- [ ] **ART-07 — Adequar o formato.** Condensar as 70 páginas atuais para 12–15 conforme `config-artigo`, preservando problema, teoria necessária, método, resultados e discussão. Corrigir resumo/legendas, títulos, paginação, citações e referências.
- [ ] **ART-08 — Vincular alegações às evidências.** Associar cada recurso/número a versão, procedimento e registro que o sustenta. Recursos não entregues ficam como limitações ou trabalhos futuros.

## 6. Ordem recomendada de execução

1. **Consolidar o evento:** validar PIR, agrupar disparos e identificar câmera/cômodo. Essa é a matéria-prima das respostas.
2. **Dar visibilidade:** diagnóstico do sensor, detalhe de evento e notificação Android no cenário definido.
3. **Corrigir consultas:** período, câmera, cômodo, pessoa, contagens, retenção e evidências; proteger acesso antes de conectar ferramentas do chat.
4. **Entregar o chat:** tela própria, contexto e um provedor funcional, medindo qualidade/custo e reaproveitando o assistente existente.
5. **Adicionar monitoramento:** regras persistentes de alerta; alterações administrativas com autenticação e confirmação verificável.
6. **Validar e documentar:** executar ensaios, reunir resultados reais e reescrever o artigo conforme a versão entregue.

**Critério de conclusão:** produzir evento real pelo PIR, receber alerta no cenário suportado e consultar período/cômodo no chat com resposta correta e evidências. Repetições do mesmo episódio não devem gerar processamento desnecessário; falhas e lacunas do histórico precisam ficar claras. Marcar entregas somente com código e registro de verificação correspondentes.
