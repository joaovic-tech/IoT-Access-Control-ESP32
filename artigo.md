# INTERNET DAS COISAS (IOT) E INTELIGÊNCIA ARTIFICIAL APLICADA À DETECÇÃO E CLASSIFICAÇÃO DE ACESSOS EM AMBIENTES PÚBLICOS E PRIVADOS

**Autoria:**
ALVES, João Victor Carvalho¹
SOBRENOME, Nome do Orientador²

¹Graduando em Engenharia de Software, [Instituição: Centro Universitario Internacional (Uninter)]. E-mail: [contato@joaovic.tech]
²Titulação e Instituição do Orientador.

---

## RESUMO

Este artigo expõe o desenvolvimento, a fundamentação teórica e a análise crítica de um sistema automatizado para monitoramento e controle de acessos, integrando as tecnologias de Internet das Coisas (IoT) e Inteligência Artificial (IA). A proposta nasce da imperativa necessidade de superar as limitações intrínsecas aos modelos tradicionais de vigilância, que dependem majoritariamente da observação humana — um processo sujeito a falhas cognitivas, fadiga perceptiva e omissões involuntárias. O sistema concebido utiliza um dispositivo IoT de baixo custo, fundamentado no módulo ESP32-CAM (baseado no microcontrolador ESP32), que opera em conjunto com sensores de movimento PIR e módulos de captura de imagem OV2640. A arquitetura da solução adota um modelo híbrido de processamento, onde a captura de dados ocorre na borda (edge) e o processamento analítico pesado é delegado a um servidor local ou em nuvem, executando algoritmos avançados de Visão Computacional para detecção e reconhecimento facial. Este estudo detalha não apenas a execução prática e a arquitetura de hardware e software, mas também discute profundamente as métricas de desempenho (latência, taxas de falso positivo/negativo) e as implicações jurídicas sob a égide da Lei Geral de Proteção de Dados (LGPD) no Brasil. Os resultados demonstram que a automação da segurança patrimonial, quando implementada com rigor técnico e conformidade legal, oferece uma alternativa robusta, escalável e economicamente viável para a proteção de ambientes residenciais, corporativos e educacionais.

**Palavras-chave:** IoT; IA; Segurança Eletrônica; Monitoramento Automatizado; Reconhecimento de Imagens.

---

## ABSTRACT

This article presents the development, theoretical foundation, and critical analysis of an automated system for access monitoring and control, integrating Internet of Things (IoT) and Artificial Intelligence (AI) technologies. The proposal arises from the imperative need to overcome the intrinsic limitations of traditional surveillance models, which rely heavily on human observation—a process subject to cognitive failures, perceptual fatigue, and involuntary omissions. The conceived system uses a low-cost IoT device based on the ESP32-CAM module (built upon the ESP32 microcontroller), operating in conjunction with PIR motion sensors and OV2640 image capture modules. The solution architecture adopts a hybrid processing model, where data capture occurs at the edge, and heavy analytical processing is delegated to a local or cloud server running advanced Computer Vision algorithms for face detection and recognition. This study details not only the practical execution and hardware and software architecture but also deeply discusses performance metrics (latency, false positive/negative rates) and legal implications under the Brazilian General Data Protection Law (LGPD). The results demonstrate that the automation of asset security, when implemented with technical rigor and legal compliance, offers a robust, scalable, and economic viable alternative for protecting residential, corporate, and educational environments.

**Keywords:** IoT; AI; Electronic Security; Automated Monitoring; Image Recognition.

---

## 1 INTRODUÇÃO

A segurança de ambientes físicos, sejam eles públicos ou privados, tem passado por uma transformação elevada nas últimas décadas. Historicamente dependente de barreiras físicas estáticas e da vigilância humana constante, o setor de segurança enfrenta agora o desafio de integrar tecnologias digitais emergentes para lidar com ameaças cada vez mais complexas e dinâmicas. A convergência entre a conectividade onipresente da Internet das Coisas (Internet of Things - IoT) e a capacidade analítica da Inteligência Artificial (IA) oferece uma oportunidade sem precedentes para redefinir os conceitos de monitoramento e controle de acesso.

Este trabalho enquadra-se na Revolução Industrial 4.0, onde a digitalização dos processos físicos permite a criação de ambientes inteligentes (smart environments). O tema central é a aplicação de sistemas embarcados conectados e algoritmos de aprendizado de máquina para automatizar a detecção, identificação e classificação de indivíduos em pontos de acesso. Diferentemente dos sistemas de Circuito Fechado de Televisão (CFTV) tradicionais, que apenas registram eventos para análise forense posterior, a solução proposta busca uma abordagem proativa: identificar e classificar o acesso em tempo real, permitindo uma resposta imediata a potenciais riscos.

A problemática central que motiva este estudo reside na falibilidade humana inerente aos processos de vigilância tradicionais. Em ambientes como escolas, condomínios residenciais e empresas, a segurança depende frequentemente de operadores que monitoram múltiplas telas de vídeo simultaneamente. Pesquisas no campo da psicologia cognitiva e ergonomia indicam que a capacidade de manter a atenção sustentada em tarefas de monitoramento visual degrada-se rapidamente. Estudos clássicos, como os citados por Tickner e Poulton, demonstram que após apenas 20 minutos de observação contínua, a taxa de detecção de eventos em telas de vigilância pode cair drasticamente, com operadores perdendo até 95% das atividades relevantes ou suspeitas. Além da fadiga, existe o problema da cegueira inintencional, onde o observador, focado em uma tarefa ou distraído por outra, falha em perceber eventos inesperados, mesmo que estes ocorram em seu campo visual direto. Em um cenário de controle de acesso, isso significa que indivíduos não autorizados podem transitar por portarias ou corredores sem serem notados, comprometendo a integridade física de pessoas e a segurança de ativos patrimoniais.

Diante deste cenário, este trabalho propõe o desenvolvimento e a análise de um dispositivo IoT integrado a um sistema de IA, capaz de monitorar entradas e saídas de forma autônoma. O objetivo geral é criar um protótipo funcional que realize a detecção de movimento, captura de imagens e classificação automática dos acessos entre conhecidos (autorizados) e desconhecidos (potenciais ameaças), enviando notificações em tempo real aos responsáveis. A proposta visa demonstrar que é possível elevar o nível de segurança e reduzir as falhas humanas através da automação tecnológica de baixo custo. Do ponto de vista econômico, sistemas de segurança biométrica comerciais costumam ter custos elevados, proibitivos para pequenas empresas ou residências populares. A utilização de hardware acessível como o ESP32-CAM democratiza o acesso a tecnologias de ponta. Do ponto de vista social e de segurança, a automação reduz o tempo de resposta a incidentes e mitiga o erro humano, proporcionando um monitoramento ininterrupto que não sofre de fadiga ou distração. Tecnicamente, o projeto justifica-se pela exploração da integração entre sistemas embarcados limitados e o processamento em borda ou servidor, um desafio relevante na engenharia de computação atual.

Para concretizar a proposta, foram delineados os seguintes objetivos específicos: fundamentar teoricamente os conceitos de IoT, IA, Visão Computacional e as normas da LGPD aplicáveis; desenvolver a arquitetura de hardware utilizando o módulo ESP32-CAM e sensores PIR para detecção de presença eficiente energeticamente; implementar o firmware de controle para captura de imagens e comunicação via protocolos web (MQTT); desenvolver um servidor de processamento utilizando a linguagem Python e a biblioteca OpenCV para detecção e reconhecimento facial; e analisar os resultados obtidos em termos de latência, precisão e viabilidade, discutindo as implicações de segurança e privacidade.

Este artigo está organizado em cinco seções. Após esta introdução, a seção 2 apresenta a Fundamentação Teórica, revisando a literatura sobre os pilares tecnológicos e legais do projeto. A seção 3 Metodologia detalha os materiais, métodos e a implementação prática do sistema. A seção 4, Resultados e Discussões, expõe os dados coletados nos testes e analisa o desempenho do sistema. Por fim, a seção 5 traz as Considerações Finais, sintetizando as conclusões e sugerindo trabalhos futuros.

---

## 2 FUNDAMENTAÇÃO TEÓRICA

A construção de um sistema de segurança inteligente exige a compreensão profunda de diversas áreas do conhecimento. Esta seção explora os conceitos de Internet das Coisas, Inteligência Artificial aplicada à visão, os paradigmas de segurança eletrônica e o arcabouço legal brasileiro de proteção de dados.

### 2.1 A REVOLUÇÃO DA INTERNET DAS COISAS (IOT)

A Internet das Coisas (IoT) não é apenas uma tendência tecnológica, mas uma mudança estrutural na forma como o mundo físico interage com o mundo digital.

#### 2.1.1 Definições e Conceitos Acadêmicos

O termo foi cunhado por Kevin Ashton em 1999, mas o conceito evoluiu significativamente. Segundo Atzori, Iera e Morabito, em seu artigo seminal "The Internet of Things: A Survey" (Atzori et al., 2010), a IoT pode ser compreendida sob três visões: orientada a coisas (focada em sensores e etiquetas RFID), orientada à internet (focada em protocolos IP) e orientada à semântica (focada no significado dos dados). A definição consolidada refere-se a uma infraestrutura de rede global, onde objetos físicos e virtuais são descobertos e integrados de forma transparente.

Dave Evans, em white paper para a Cisco, descreve IoT como o ponto na história onde mais "coisas" ou objetos estavam conectados à Internet do que pessoas, estimando que bilhões de dispositivos estariam interconectados, gerando dados que poderiam "mudar tudo", desde a gestão urbana até a segurança pessoal (Evans, 2011).

#### 2.1.2 Arquitetura de Sistemas IoT

Para fins de segurança e vigilância, a arquitetura de IoT é geralmente dividida em camadas, conforme descrito na literatura técnica:

A Camada de Percepção (Perception Layer) constitui a base física do sistema, incluindo sensores como o PIR HC-SR501 e dispositivos de captura como a câmera OV2640 no ESP32-CAM. Sua função é coletar dados brutos do ambiente e transformá-los em sinais digitais.

A Camada de Rede (Network/Transport Layer) é responsável pela transmissão confiável dos dados coletados para o sistema de processamento, utilizando tecnologias como Wi-Fi, Bluetooth, Zigbee ou LoRaWAN. No contexto deste projeto, o foco é a transmissão de imagens via Wi-Fi.

A Camada de Processamento (Processing Layer) é onde os dados são armazenados e analisados, podendo ocorrer na nuvem (Cloud Computing) ou na borda (Edge Computing). A computação em borda é crítica para segurança, pois reduz a latência e a dependência de conectividade externa constante.

A Camada de Aplicação (Application Layer) representa a interface com o usuário final, onde os alertas são recebidos e as decisões são tomadas, como em aplicativos móveis ou painéis de controle web.

#### 2.1.3 Protocolos de Comunicação: HTTP vs. MQTT

A escolha do protocolo de comunicação é crucial para a eficiência do sistema.

O protocolo HTTP (Hypertext Transfer Protocol) é amplamente utilizado para transmissão de imagens e streaming de vídeo (MJPEG) devido à sua simplicidade e compatibilidade nativa com navegadores web. No entanto, o HTTP possui um overhead (cabeçalho) grande, o que pode aumentar a latência e o consumo de energia em conexões frequentes.

O protocolo MQTT (Message Queuing Telemetry Transport) é um protocolo leve do tipo publish-subscribe, ideal para dispositivos com recursos limitados e redes instáveis. Estudos comparativos mostram que o MQTT sobre TCP apresenta menor latência média (aprox. 290ms) e maior estabilidade do que o HTTP (aprox. 342ms) em cenários de IoT (AMIRKHANOV et al., 2025). Para sistemas de segurança que exigem alertas instantâneos, o MQTT é superior, embora o HTTP seja muitas vezes necessário para o transporte de cargas de dados maiores (como arquivos de imagem completos) em implementações simples.

### 2.2 INTELIGÊNCIA ARTIFICIAL E VISÃO COMPUTACIONAL

A integração da IA transforma câmeras passivas em sensores inteligentes capazes de "entender" a cena.

#### 2.2.1 Conceitos de IA segundo Russell e Norvig

Stuart Russell e Peter Norvig, em sua obra clássica "Artificial Intelligence: A Modern Approach" (Russell e Norvig, 2010), definem IA não apenas como a simulação da inteligência humana, mas como o estudo de agentes racionais. Um agente é qualquer coisa que percebe seu ambiente via sensores e age sobre esse ambiente através de atuadores.

No contexto deste projeto, o sistema de segurança é um "agente racional" que percebe a presença (sensor PIR), captura o estado do mundo (câmera) e age (destrava a porta ou envia alerta) para maximizar sua medida de desempenho (segurança e conveniência).

#### 2.2.2 Processamento Digital de Imagens e Visão Computacional

A Visão Computacional é a subárea da IA que lida com a interpretação de dados visuais. Segundo Gonzalez e Woods em "Processamento Digital de Imagens", o processo de visão computacional pode ser dividido em fases fundamentais: aquisição da imagem, que ocorre via captura pelo sensor OV2640; pré-processamento, referente à melhoria da imagem (ajuste de brilho/contraste, conversão para escala de cinza) para facilitar a análise; segmentação, que corresponde ao isolamento dos objetos de interesse (neste caso, a face) do fundo da imagem; representação e descrição, relativa à extração de características (features) que tornam aquele objeto único; e reconhecimento e interpretação, que consiste na atribuição de um rótulo (identidade) ao objeto com base em seus descritores.

Richard Szeliski, em "Computer Vision: Algorithms and Applications" (Szeliski, 2010), complementa que os algoritmos modernos de visão dependem fortemente de aprendizado de máquina estatístico para realizar o reconhecimento robusto em ambientes não controlados (variações de luz, pose e expressão).

#### 2.2.3 Algoritmos de Detecção e Reconhecimento Facial

Para a execução prática, é essencial distinguir entre detecção e reconhecimento. A detecção facial consiste em encontrar onde está o rosto na imagem. O algoritmo Viola-Jones (Haar Cascades) é um método clássico, extremamente rápido e leve, adequado para pré-processamento, embora propenso a falsos positivos em cenários complexos. Redes Neurais Convolucionais (CNNs), como o MTCNN ou modelos baseados em YOLO (You Only Look Once), oferecem precisão muito superior, detectando faces em diversos ângulos e escalas, mas exigem maior poder computacional.

O reconhecimento facial, por sua vez, busca identificar quem é a pessoa. Métodos tradicionais como Eigenfaces (PCA) e Fisherfaces (LDA) foram amplamente substituídos por modelos de Deep Learning como o FaceNet (Google) ou Dlib (ResNet). O Dlib, por exemplo, mapeia uma face em um vetor de 128 dimensões, onde a distância euclidiana entre vetores corresponde à similaridade facial, alcançando acurácia superior a 99% em benchmarks padrão.

### 2.3 SEGURANÇA ELETRÔNICA E CONTROLE DE ACESSO

O controle de acesso físico visa garantir que apenas pessoas autorizadas entrem em um determinado espaço.

#### 2.3.1 Paradigma de Segurança Física e "Defesa em Profundidade"

A segurança patrimonial baseia-se no conceito de "Defesa em Profundidade" (Defense in Depth), que prega a utilização de múltiplas camadas de segurança redundantes. O controle de acesso automatizado atua como uma barreira de triagem crítica. Diferente de chaves físicas, que podem ser copiadas, ou cartões RFID, que podem ser roubados, a biometria facial vincula o acesso intrinsecamente ao indivíduo, elevando o nível de segurança (fator "o que você é" vs. "o que você tem").

#### 2.3.2 Métricas de Desempenho Biométrico

A eficácia de um sistema biométrico é avaliada estatisticamente. O FAR (False Acceptance Rate) representa a probabilidade de o sistema aceitar incorretamente uma pessoa não autorizada. Em segurança, o objetivo primário é minimizar o FAR (tender a zero). O FRR (False Rejection Rate) representa a probabilidade de o sistema rejeitar incorretamente uma pessoa autorizada. Um FRR alto causa inconveniência e frustração ao usuário. A Curva ROC (Receiver Operating Characteristic) representa graficamente a relação entre a taxa de verdadeiros positivos e falsos positivos. O ajuste do limiar de sensibilidade (threshold) do algoritmo de IA move o sistema ao longo desta curva, trocando conveniência (baixo FRR) por segurança (baixo FAR).

### 2.4 ASPECTOS LEGAIS: A LGPD E O TRATAMENTO DE BIOMETRIA

A implementação tecnológica não ocorre no vácuo jurídico. No Brasil, a Lei Geral de Proteção de Dados (Lei nº 13.709/2018) impõe diretrizes estritas.

A LGPD define dados biométricos (como a imagem facial utilizada para identificação única) como dados pessoais sensíveis (Art. 5º, II). O tratamento desses dados é vedado, exceto em hipóteses específicas (Art. 11), como:

- Quando o titular ou seu responsável legal consentir, de forma específica e destacada, para finalidades específicas;
- Sem fornecimento de consentimento, nas hipóteses de garantia da prevenção à fraude e à segurança do titular, nos processos de identificação e autenticação de cadastro em sistemas eletrônicos (Art. 11, II, g).

Isso implica que condomínios e empresas que adotam reconhecimento facial devem:

- Realizar um Relatório de Impacto à Proteção de Dados Pessoais (RIPD);
- Garantir a transparência, informando claramente aos usuários sobre a captura e uso das imagens;
- Implementar medidas robustas de segurança da informação (criptografia, controle de acesso aos logs) para evitar vazamentos, visto que dados biométricos, uma vez comprometidos, não podem ser "trocados" como uma senha.

### 2.5 ANÁLISE CONTEXTUAL E PROCESSAMENTO DE LINGUAGEM NATURAL (PLN)

Enquanto a Visão Computacional, discutida anteriormente, resolve o problema da percepção (identificar quem está no ambiente), a segurança moderna exige uma camada superior de inteligência capaz de interpretar o contexto (analisar se a presença daquele indivíduo é permitida naquele momento e local).

Segundo Russell e Norvig (2010), um agente inteligente não apenas percebe o ambiente através de sensores, mas atua sobre ele para alcançar um objetivo. No contexto deste trabalho, o "objetivo" é a segurança patrimonial. Para isso, o sistema deve operar não apenas com reconhecimento de padrões (redes neurais), mas também com inferência lógica e regras de conhecimento.

A integração de modelos de linguagem e processamento de dados contextuais permite transformar o dado bruto ("Rosto de João detectado") em informação semântica ("João, funcionário do turno da manhã, detectado fora de horário"). Essa abordagem reduz a taxa de falsos positivos operacionais, pois o sistema passa a validar a detecção contra um conjunto de regras dinâmicas (horários, permissões, calendário escolar/empresarial), aproximando-se do conceito de "Computação Ciente de Contexto" (Context-Aware Computing).

Dessa forma, a arquitetura proposta neste trabalho utiliza a Visão Computacional para a geração de dados e um motor de inferência lógica para a classificação do risco, garantindo que o alerta seja disparado apenas em situações de real anomalia.

---

## 3 METODOLOGIA

### 3.1 VISÃO GERAL DA ARQUITETURA

A solução proposta foi desenvolvida sobre uma arquitetura híbrida de IoT, dividida em duas camadas principais: a Camada de Borda (Edge Layer), responsável pela captura e transmissão eficiente de dados, e a Camada de Servidor (Server Layer), responsável pelo processamento pesado de Inteligência Artificial e lógica de negócios.

O fluxo do sistema foi projetado para maximizar a eficiência energética e minimizar a latência. O dispositivo de borda (ESP32-CAM) permanece em estado de baixo consumo (Deep Sleep) até que um evento físico seja detectado, momento em que acorda, captura a imagem e a transmite para o servidor via protocolo MQTT.

Sendo assim, o servidor, por sua vez, processa a imagem, identifica o indivíduo e valida as regras de acesso baseadas em contexto (horário e permissões).

A Figura 1 apresenta o protótipo físico final do sistema, demonstrando a montagem e integração dos componentes de hardware.

**Figura 1 - Protótipo físico do sistema IoT**

![Protótipo físico do sistema IoT](./docs/images/figura_1_prototipo_final.jpg)

Fonte: O autor (2026).

### 3.2 FLUXO DE DADOS E FUNCIONAMENTO

O funcionamento do sistema segue um diagrama de fluxo linear e determinístico, composto pelas seguintes etapas: detecção de presença, onde o sensor PIR (HC-SR501) detecta variação infravermelha no ambiente e envia um sinal HIGH para o pino de Wake-up (GPIO 13) do microcontrolador; inicialização e captura, em que o ESP32-CAM desperta do modo Deep Sleep, inicializa o módulo de câmera OV2640 e captura um quadro (frame) em resolução VGA (640x480); transmissão, quando a imagem é fragmentada em payloads binários e publicada no tópico MQTT cam/image através da rede Wi-Fi; recepção e remontagem, na qual o servidor, por meio de um script Python inscrito no tópico, recebe os fragmentos, remonta a imagem e a encaminha para o pipeline de IA; identificação e validação, onde o sistema extrai o vetor biométrico da face, compara com o banco de dados e, em caso de match positivo, consulta o Motor de Regras (TBAC) para verificar se aquele usuário tem permissão para aquele horário; e ação, momento em que, baseado na validação, o sistema aciona o relé (abertura de porta) ou registra um alerta de segurança.

### 3.3 DESENVOLVIMENTO DO FIRMWARE (EDGE)

O firmware do dispositivo IoT foi desenvolvido em C++ utilizando a plataforma Arduino IDE. O código prioriza a velocidade de conexão e a gestão de energia.

#### 3.3.1 Configuração da Câmera

Utilizou-se a biblioteca esp_camera.h para configurar o sensor OV2640. Para garantir o equilíbrio entre qualidade para reconhecimento facial e velocidade de transmissão, o formato de píxel foi definido como JPEG com qualidade 10 (escala de 0-63, onde menor é melhor) e resolução VGA.

**Figura 2 - Módulo ESP32-CAM com câmera OV2640 utilizado no projeto**

![ESP32-CAM com câmera OV2640](./docs/images/figura_2_placa_adaptada.jpg)

Fonte: O autor (2026).

#### 3.3.2 Transmissão via MQTT

A comunicação utiliza a biblioteca PubSubClient. Diferente do protocolo HTTP, que exige um handshake TCP a cada requisição (aumentando o overhead), o MQTT mantém a conexão ativa ou a restabelece rapidamente com um payload de cabeçalho mínimo (aprox. 2 bytes), ideal para redes instáveis.

#### 3.3.3 Gestão de Energia (Deep Sleep)

Foi implementada a função esp_deep_sleep_start(), configurando o ESP32 para hibernação profunda. Neste modo, a CPU, o Wi-Fi e a câmera são desligados, mantendo-se apenas o processador ULP (Ultra Low Power) ativo para monitorar o sinal do sensor PIR.

### 3.4 DESENVOLVIMENTO DO SERVIDOR (BACKEND)

O servidor de processamento foi implementado em linguagem Python 3.9, escolhida pela vasta disponibilidade de bibliotecas para Visão Computacional e IoT.

#### 3.4.1 Recepção de Dados (MQTT Subscriber)

Utilizou-se a biblioteca paho-mqtt para implementar o cliente assinante (subscriber). O sistema opera de forma assíncrona (non-blocking), permitindo que o servidor processe múltiplas requisições de diferentes câmeras simultaneamente sem travamento do thread principal.

#### 3.4.2 Pipeline de IA (FaceNet)

O reconhecimento facial utiliza a biblioteca dlib com o modelo ResNet pré-treinado. O processo ocorre em três subetapas: detecção da face via HOG (Histogram of Oriented Gradients); alinhamento dos pontos fiduciais; e extração do embedding (vetor de 128 dimensões). A comparação é feita pelo cálculo da Distância Euclidiana, com um limiar (threshold) ajustado para 0.6.

#### 3.4.3 Motor de Regras e Controle de Acesso (TBAC)

Foi desenvolvido um módulo lógico de TBAC (Time-Based Access Control). Diferente de sistemas passivos, este motor cruza a identidade detectada com uma matriz de permissões JSON {usuario_id, horarios_permitidos, zonas_autorizadas}. Se um usuário autorizado for detectado fora de seu turno, o sistema gera um alerta de "Anomalia Contextual" em vez de liberar o acesso.

---

## 4 RESULTADOS E DISCUSSÕES

---

## 5 CONSIDERAÇÕES FINAIS

---

## REFERÊNCIAS

AMIRKHANOV, Bauyrzhan et al. Evaluating HTTP, MQTT over TCP and MQTT over WEBSOCKET for digital twin applications: A comparative analysis on latency, stability, and integration. **International Journal of Innovative Research and Scientific Studies**, v. 8, n. 1, p. 679-694, 2025. Disponível em: <https://www.ijirss.com/index.php/ijirss/article/view/4414>. Acesso em: 26 jan. 2026.

ATZORI, L.; IERA, A.; MORABITO, G. The Internet of Things: A survey. **Computer Networks**, v. 54, n. 15, p. 2787–2805, 2010.

BIGDELI, Ali. **RealTime-MQTT-Camera**: a brief example of mqtt usage to send camera stream to web page. GitHub, 2021. Disponível em: <https://github.com/AliBigdeli/RealTime-MQTT-Camera>. Acesso em: 26 jan. 2026.

BRASIL. Lei nº 13.709, de 14 de agosto de 2018. Lei Geral de Proteção de Dados Pessoais (LGPD). **Diário Oficial da União**, Brasília, DF, 2018.

DIXIT, Pranav. **Time-Locked Access in Django**: Implementing Time-Based Access Control (TBAC). Medium, 2020. Disponível em: <https://medium.com/@pranavdixit20/time-locked-access-in-django-implementing-time-based-access-control-tbac-efef1382738b>. Acesso em: 26 jan. 2026.

ESP32 COMMUNITY. **ESP32-CAM and PIR sensor**. ESP32 Forum, 2024. Disponível em: <https://esp32.com/viewtopic.php?t=37906>. Acesso em: 26 jan. 2026.

EVANS, D. **The Internet of Things**: How the Next Evolution of the Internet Is Changing Everything. Cisco Internet Business Solutions Group (IBSG), 2011.

FLESPI PLATFORM. **HTTP vs MQTT performance tests**. Medium, 2017. Disponível em: <https://medium.com/@flespi/http-vs-mqtt-performance-tests-f9adde693b5f>. Acesso em: 26 jan. 2026.

GONZALEZ, R. C.; WOODS, R. E. **Processamento Digital de Imagens**. 3. ed. São Paulo: Pearson Prentice Hall, 2010.

KAGGLE. **Comparison of face detection packages**. Disponível em: <https://www.kaggle.com/code/timesler/comparison-of-face-detection-packages/comments>. Acesso em: 26 jan. 2026.

LAST MINUTE ENGINEERS. **Insight Into ESP32 Sleep Modes & Power Consumption**. Disponível em: <https://lastminuteengineers.com/esp32-sleep-modes-power-consumption/>. Acesso em: 26 jan. 2026.

REAL TIME LOGIC. **Streaming ESP32-CAM Images to Multiple Browsers via MQTT**. Disponível em: <https://realtimelogic.com/articles/Streaming-ESP32CAM-Images-to-Multiple-Browsers-via-MQTT>. Acesso em: 26 jan. 2026.

RESEARCHGATE. **A lightweight deep learning model for real‐time face recognition**. Disponível em: <https://www.researchgate.net/publication/372957188>. Acesso em: 26 jan. 2026.

RUSSELL, S. J.; NORVIG, P. **Artificial Intelligence: A Modern Approach**. 3. ed. Upper Saddle River: Prentice Hall, 2010.

SZELISKI, Richard. **Computer Vision**: Algorithms and Applications. Springer Science & Media, 2010.

TICKNER, A. H.; POULTON, E. C. Monitoring surveillance pictures. **Ergonomics**, v. 16, n. 4, 1973.

TSAI, Wakeup. **[Image-MQTT] Publish and subscribe the images through MQTT**. GitHub Gist, 2019. Disponível em: <https://gist.github.com/WakeupTsai/6cac70f8e9f26cc909e9223346580a0f>. Acesso em: 26 jan. 2026.
