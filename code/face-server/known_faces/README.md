# known_faces/

Amostras faciais usadas para gerar os *encodings* do reconhecimento.

Estrutura (uma subpasta por pessoa, nomeada com o `id` gerado pelo servidor):

```
known_faces/
└── 6f2a1c9b4e70/       <- id da pessoa (people/6f2a1c9b4e70.json)
    ├── 1.jpg
    ├── 2.jpg
    └── 3.jpg
```

Regras:

- 3 a 5 amostras por pessoa, em angulos diferentes e boa iluminacao;
- **exatamente um rosto por imagem** (o servidor rejeita amostras com 0 ou 2+);
- as pastas sao criadas automaticamente por `POST /people`; nao e necessario
  criar nada a mao.

O conteudo destas subpastas **nao e versionado** (dado biometrico pessoal) --
ver `.gitignore` na raiz do repositorio.
