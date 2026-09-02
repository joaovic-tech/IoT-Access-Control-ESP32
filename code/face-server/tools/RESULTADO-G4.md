# G4 — Varredura do limiar de reconhecimento (FAR × FRR)

Execução em **30/08/2026** com `tools/tolerance_experiment.py`, sobre as amostras
cadastradas em `known_faces/` e as detecções gravadas em `detections/`.

## Conjunto medido

| Pessoa | Amostras |
|---|---|
| Antônio | 1 |
| Lincoln | 1 |
| João Victor | 2 |

**1 par genuíno, 5 pares impostores.** É pouco: a curva serve para orientar a
escolha do limiar neste ambiente, **não** como resultado estatístico. Ver §
"Limitações".

## Curva

| Limiar | FAR | FRR |
|---|---|---|
| 0.30 – 0.34 | 0.000 | 1.000 |
| **0.36 – 0.50** | **0.000** | **0.000** |
| 0.52 – 0.58 | 0.200 | 0.000 |
| **0.60** (atual) | **0.400** | 0.000 |
| 0.62 – 0.70 | 0.400 | 0.000 |

EER aproximado em **0.36** (FAR = 0, FRR = 0). A faixa **0.36–0.50** zera os dois
erros neste conjunto.

## Achado principal

Com o limiar de produção (`TOLERANCE = 0.6`), **2 dos 5 pares impostores são
aceitos**:

```
Antônio  x  João Victor  = 0.506   <-- aceito como a mesma pessoa
Antônio  x  João Victor  = 0.582   <-- aceito como a mesma pessoa
Antônio  x  Lincoln      = 0.760   ok
Lincoln  x  João Victor  = 0.732   ok
Lincoln  x  João Victor  = 0.728   ok
```

Ou seja: **o sistema pode reconhecer Antônio como João Victor** (que tem acesso a
todos os cômodos). Num controle de acesso físico isso é uma falsa aceitação com
consequência direta — a fechadura abriria para a pessoa errada.

## Distâncias observadas em operação

31 detecções com `status: recognized`:

| | valor |
|---|---|
| mínima | 0.337 |
| média | 0.469 |
| máxima | 0.582 |

A máxima observada (0.582) coincide com a distância Antônio × João Victor — é
coerente com a hipótese de que **parte dessas 31 "reconhecidas" foram trocas de
identidade**, não acertos.

> Estes dados não têm verdade de referência (ninguém confirmou quem era de fato
> cada rosto), então descrevem a operação — não substituem FAR/FRR.

## Recomendações

1. **Baixar `TOLERANCE` de 0.6 para ~0.45.** Fica dentro da faixa de erro zero
   medida e rejeita os dois pares problemáticos com folga. *Não foi alterado
   automaticamente:* mexer no limiar de um sistema de acesso é decisão do autor.
2. **Cadastrar mais amostras por pessoa.** Antônio e Lincoln têm **1** amostra
   cada; o próprio servidor considera "ativo" a partir de 3
   (`MIN_SAMPLES_ACTIVE`). Mais amostras aproximam os pares genuínos e afastam
   os impostores, aumentando a margem entre as distribuições.
3. **Refazer esta medição** depois de (2), e novamente após qualquer mudança no
   pipeline (ex.: G1/G2).

## Limitações (registrar no TCC)

- **1 par genuíno e 5 impostores** não sustentam uma curva ROC. Cada ponto da
  curva se apoia em pouquíssimas observações.
- Para o texto do TCC, repetir com um conjunto público:
  `python tools/tolerance_experiment.py --dataset <pasta>/lfw`
  (LFW: http://vis-www.cs.umass.edu/lfw/), ou cadastrar mais voluntários.
- As distâncias de operação não são rotuladas, então servem como estatística
  descritiva.

## Como reproduzir

```bash
cd code/face-server
.venv\Scripts\python.exe tools/tolerance_experiment.py --com-historico
```

Saída tabular no terminal + `tools/tolerance_curve.csv` para plotar.
