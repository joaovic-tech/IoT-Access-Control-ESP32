# AGENTS.md

## 1) Visão geral do projeto

Este projeto é um sistema de controle de acesso IoT baseado em ESP32, com foco em:

- Detecção de moviemento (dispositivo sensor pir)
- Reconhecimento facil usando a lib openCV do python (servidor interno) - códico-fonte:  @code/CameraWebServer
- Registro em tempo real do vídeo (ESP32-cam) - códico-fonte: @code/CameraWebServer
- Visualização e configuração das cameras e controle de disposivos em um aplicativo - códico-fonte: @code/ESPCamApp

O objetivo principal é construir um dispositivo embarcado que controle acesso físico de forma automatizada e monitorada.

## 2) Tecnologias

Dependendo da implementação do projeto, o stack pode incluir:

### Dispositivos:
- ESP32-cam junto com o modulo ESP32-cam-mb para fins de transferencia de código para o dispositivo.
- SENSOR-PIR para detecção de movimento.

### Linguagens
- C/C++: Servidor do dispositivo para acessar as gravações.
- Python: API para fazer o reconhecimento das faces
- Kotlin: Aplicativo onde terá a finalidade de registrar um dispositivo novo, ver em tempo real a camera do ESP32-cam, e controlete e monitoramento das pessoas (facial)

Atenção: sempre prefira soluções compatíveis com o hardware e com as restrições de memória/energia do ESP32.

## 3) Estrutura sugerida do repositório (Não deve ser alterada)

```text
.
├── assets/                 # Prints de testes e funcionanmento do projeto.
├── code/                   # códigos principais do dispositivo, servidor de reconhecimento facil e aplicativo movel.
├── config-artigo/          # Instruções, orientações sobre como desenvolver o  artigo e como será feito a avaliação.
├── docs/                   # documentação do projeto.
├── README.md               # visão geral do projeto e instruções de uso
├── AGENTS.md               # orientações para assistentes de IA
└── tasks.md                # Tarefas a serem realizadas ou marcadas como concluídas
```

Se a estrutura do repositório for diferente, siga a organização real do projeto e mantenha consistência.

## 4) Regras de desenvolvimento

### 4.1 Qualidade do código

- escreva código limpo, modular e fácil de manter;
- use nomes descritivos para funções, variáveis e arquivos;
- evite duplicação de lógica;
- documente trechos complexos com comentários curtos e objetivos;
- prefira constantes e enumerações para valores fixos;
- valide entradas de sensores, credenciais e comandos externos.
- Só crie testes com autorização do usuário

### 4.2 Firmware embarcado

- tenha atenção ao uso de memória RAM e flash;
- evite loops longos e bloqueantes desnecessários;
- prefira funções assíncronas ou tarefas quando necessário;
- use timeouts para comunicação sem fio e periféricos;
- trate falhas de rede, sensores e dispositivos de forma segura;
- em caso de reset ou reinicialização, o sistema deve recuperar de forma determinística.

### 4.3 Segurança

- nunca grave segredos em código-fonte em texto puro;
- use chaves, tokens e credenciais via configuração segura ou ambiente;
- valide autenticação antes de liberar acesso físico;
- registre eventos de autenticação falha/sucesso;
- tenha cuidado com a exposição de endpoints e APIs.

## 5) Convenções de nomenclatura

- arquivos em snake_case ou kebab-case conforme padrão do projeto;
- funções em snake_case;
- constantes em UPPER_SNAKE_CASE;
- classes/estruturas em PascalCase (se aplicável);
- nomes de variáveis em português ou inglês, mas sempre consistentes no projeto;
- use comentários em inglês se o código estiver em inglês; caso contrário, mantenha o idioma do restante do projeto.

## 6) Fluxo de trabalho recomendado

1. entenda o requisito antes de alterar o código;
2. identifique o módulo afetado (RFID, Wi-Fi, acesso, logs, relé, etc.);
3. implemente a menor mudança necessária;
4. valide o comportamento com testes, simulação ou compilação;
5. mantenha compatibilidade com o restante do sistema;
6. documente mudanças relevantes no README ou na documentação do módulo.

## 7) Diretrizes para o Codex

Ao trabalhar neste repositório, o Codex deve:

- respeitar a arquitetura e convenções existentes;
- manter o código compilável e consistente;
- evitar alterações fora do escopo solicitado;
- priorizar correções pequenas e focadas;
- explicar mudanças importantes em linguagem clara;
- quando necessário, propor melhorias sem quebrar comportamento atual;
- reusar módulos e helpers existentes antes de criar novas abstrações.

## 8) Checklist antes de finalizar

Antes de encerrar qualquer tarefa, confirme:

- [ ] o código compila ou passa nos testes relevantes;
- [ ] a lógica foi validada para o caso de uso do sistema;
- [ ] não houve regressões em módulos relacionados;
- [ ] comentários e documentação foram ajustados quando necessário;
- [ ] logs e tratamento de erros estão apropriados;
- [ ] a alteração respeita as restrições de hardware e memória do ESP32.

## 9) Exemplos de prompts úteis

- "Adicione suporte para leitura de cartão RFID no módulo de autenticação."
- "Corrija o problema de conexão Wi-Fi que reinicia o ESP32 após falha de rede."
- "Crie uma rotina de log para registrar acessos e rejeições."
- "Melhore a estrutura do código para reduzir uso de memória em operação contínua."

## 10) Observações finais

Este arquivo deve servir como guia rápido para qualquer agente de IA ou colaborador que trabalhe no projeto. Mantenha-o atualizado conforme a arquitetura evolui.

Se necessário, o projeto pode ser expandido com:

- painel web ou mobile;
- armazenamento em banco de dados em nuvem;
- autenticação centralizada;
- integração com câmeras ou reconhecimento facial;
- dashboards de monitoramento e relatórios.

Em resumo: foque em robustez, segurança, baixo consumo e confiabilidade para operação de controle de acesso em ambiente real.

OBS: Dentro do ESP32-cam existe um cartão sd de 2GB
