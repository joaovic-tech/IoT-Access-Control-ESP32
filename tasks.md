- leia o arquivo claude.md no diretorio raiz deste projeto e faça as tarefas pendentes abaixo

## Tela de Pessoas:
- [x] Quando crio uma pessoa nova ou edito e envio 5 fotos de uma só vez são cadastradas somente uma foto ou ou mais porem nunca todas as que eu quero cadastrar.
  - Causa: o app enviava as 5 (confirmado no servidor instrumentado), mas o detector
    HOG do dlib só acha rosto de frente — foto de perfil, de queixo erguido ou de
    cabeça baixa volta com "0 rosto(s) encontrados" e é recusada. A tela voltava
    para a lista na mesma hora, sem dizer quais caíram nem por quê.
  - Correção: a tela agora fica aberta quando alguma foto é recusada, marca as
    recusadas na grade, explica o motivo e passa a editar a pessoa já criada
    (salvar de novo não cria cadastro repetido). O texto de ajuda pede rosto de
    frente, e o servidor responde `refused` (arquivo + rostos + motivo).
