# CircuitoLab

Simulador de circuitos elétricos em **Java puro** (JDK 21, zero dependências externas).
Desenha o circuito na tela, resolve a análise DC por **Análise Nodal Modificada (MNA)**
e mostra tensões nos nós, correntes e potências em cada componente.

> **EN summary:** Pure-Java (JDK 21, no dependencies) DC circuit simulator.
> Draw the circuit in the Swing GUI (or load a JSON file), solve it with
> Modified Nodal Analysis, and get node voltages, branch currents and power.
> CLI mode: `java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json`.

## Como compilar e rodar

Requisito: JDK 21 (sem Maven, sem Gradle, sem libs externas).

```sh
./build.sh        # compila app (out/) e testes (out-test/)
./run.sh          # abre a GUI
./run-tests.sh    # corre os testes do solver (21 testes)
```

No Windows: `run.bat` (JDK 21 no `PATH`).

Modo CLI (sem GUI):

```sh
./build.sh
java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json
java -cp out com.circuitolab.app.Main --solve examples/ponte-wheatstone.json --csv out.csv --report out.txt
# Passo a passo simples no terminal (ver abaixo — é o padrão):
java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json --passo-a-passo
java -cp out com.circuitolab.app.Main --solve examples/delta-estrela.json --steps
# Versão completa, com matriz (ver abaixo):
java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json --passo-a-passo-completo
# Passo a passo em PDF, com o desenho do circuito (ver abaixo):
java -cp out com.circuitolab.app.Main --solve examples/serie.json --passo-a-passo --pdf serie-passo-a-passo.pdf
java -cp out com.circuitolab.app.Main --solve examples/delta-estrela.json --pdf delta-passo-a-passo.pdf
```

## Como usar (GUI)

1. Escolha uma ferramenta na barra superior: Resistor, Fonte V, Fonte I,
   Capacitor, Indutor, Fio, Terra, Mover ou Apagar.
2. **Clique-arraste** no canvas para desenhar o componente entre dois pontos
   da grade (snap de 20 px; clicar a até 12 px de um nó reutiliza esse nó).
   Um clique simples sem arrastar não cria nada — nem nó, nem componente.
3. **Duplo clique** num componente edita o **rótulo** (ex.: `R1`) e o
   **valor** (ex.: `2k` = 2000 Ω, `1,5`, `10m` = 0,01 A), sem criar nós
   soltos. O rótulo tem de ser único; o id interno não muda.
4. **Resolver (Ctrl+R)** roda o solver: as tensões aparecem junto aos nós,
   as correntes junto aos componentes, e as tabelas no painel lateral.
5. Menu **Arquivo**: Novo / Abrir / Salvar (JSON próprio) / Exportar CSV / TXT
   / **Exportar passo a passo (PDF)...** / **Limpar nós soltos** (remove nós
   sem componente ligado).
   Menu **Editar**: **Renumerar componentes e nós** — renumera os rótulos
   em ordem de criação, por tipo (R1, R2, R3...; V1, V2...; o terra não
   muda) e os nós com ids sequenciais (terra continua N0); depois resolve
   de novo. As tensões e correntes não mudam — só os números.
   Menu **Exemplos**: Divisor de Tensão, Ponte de Wheatstone, Série,
   Paralelo, Delta-Estrela.
6. **Terra é obrigatória:** pelo menos um componente tem de tocar o nó terra
   (use a ferramenta Terra ou ligue um componente ao símbolo de terra).
   Sem terra, o solver recusa-se a resolver e explica porquê.
7. O símbolo **`+` da fonte de tensão é desenhado junto ao terminal A**
   (o terminal + real) e o `−` junto ao terminal B, conforme a direção
   em que a fonte foi desenhada — o `+` já não fica sempre em cima.

## Passo a Passo (o cálculo, explicado conforme o circuito)

O botão **Passo a Passo** (na barra de ferramentas) e o menu
**Exibir > Passo a Passo...** resolvem o circuito atual e abrem um diálogo
com duas abas: **Simples** (abre nela) e **Completa (com matriz)**.

### Versão simples (o padrão)

Linguagem de leigo, frases curtas, só Lei de Ohm e LKC — sem matriz,
sem Gauss, sem "condutância". Antes de qualquer conta, o passo a passo
junta os nós que são o mesmo ponto elétrico: um fio, um indutor ou o
terra, em DC, são um curto (0 V), por isso os nós que eles ligam têm a
mesma tensão e contam como um ponto só (ex.: N0, N4, N6 e N8 ligados por
fios são o terra, 0 V — e não quatro incógnitas separadas). Depois
separa o que já se sabe do que falta descobrir:

1. **O que a gente quer descobrir** — tensões e correntes, e o desenho
   em poucas linhas (cada componente com rótulo, valor e nós A/B).
2. **O que já sabemos sem fazer conta** — o terra (0 V), os pontos que
   são o mesmo que o terra, e os nós ligados direto a uma fonte
   (ex.: "o nó N3 está em 10 V: a fonte V1 liga-o ao terra").
3. **O que falta descobrir** — só as tensões que não se sabem (na maior
   parte dos circuitos é uma só).
4. **A conta** — a LKC no ponto, em palavras e com os números; com uma
   só incógnita, a álgebra toda até isolar ela, passo a passo
   (juntar os termos, dividir). Com duas ou mais, mostra as equações
   e os valores, e a resolução completa está na versão completa.
5. **As correntes** — cada resistor pela Lei de Ohm, com a conta e o
   sentido em palavras; fios e fontes numa frase curta cada um.
6. **Confere?** — no ponto principal, o que entra = o que sai, e o
   balanço de potência (fornecido = dissipado), uma linha cada.

### Versão completa (com matriz)

O cálculo completo, explicado como um tutorial — dá para acompanhar
e perceber o que está a acontecer, não só copiar o resultado. O texto
usa sempre os rótulos e os números dos nós do desenho, com unidades
amigáveis (mA, kΩ, ...) e 3 casas decimais nas explicações; a matriz
mantém 6 casas decimais.

0. **Mapa do circuito** — cada componente com rótulo, tipo, valor e os
   nós A e B; cada nó com a tensão e os componentes ligados nele. É a
   correspondência entre o desenho e o texto: confere aqui que os
   números do passo a passo são os do desenho.
1. **O que vamos calcular e porquê** — tensão de nó, terra como referência
   0 V, e o que é cada incógnita x1, x2... (que nó / que corrente).
2. **Equações (LKC), nó por nó** — cada termo explicado em palavras e com
   os valores substituídos; o que é a condutância (1/R) quando aparece.
3. **Como a matriz A é montada** — linha = equação de um nó, coluna =
   uma incógnita, diagonal = soma das condutâncias, fora da diagonal =
   −condutância, lado direito = fontes. Matriz aumentada [A | b].
4. **Eliminação de Gauss** — cada passo explicado (qual o pivô e porquê,
   qual o fator, qual a operação de linha e o que ela significa), com a
   matriz depois de cada passo.
5. **Retrosubstituição** — cada incógnita resolvida mostrando a
   substituição com os números.
6. **Resultado e conferência** — tensões, correntes de cada componente
   pela Lei de Ohm com a conta mostrada, explicação do sinal negativo
   (sentido real ao contrário do desenhado), conferência da LKC em cada
   nó com os números e balanço de potência (fornecido = dissipado).
7. **Conversão Delta→Estrela** — só quando o circuito tem um triângulo
   de resistores: as fórmulas `R_Y = R·R / (soma dos 3 R)`, os valores
   e a tensão do centro da estrela.

No CLI, a versão simples sai no terminal com `--passo-a-passo` (ou
`--steps`); a completa, com `--passo-a-passo-completo` (ou `--completo`).
O relatório TXT (`--report` / Exportar relatório) inclui o passo a passo
no fim do ficheiro.

### Exportar o passo a passo para PDF (com o desenho)

O PDF é gerado em Java puro (sem bibliotecas externas): A4, cabeçalho com
o nome do circuito, rodapé com o número da página, o desenho do circuito
renderizado, primeiro a **versão simples** do passo a passo e, como
**apêndice**, a versão completa (com matriz).

- **Na GUI:** botão **Exportar PDF** no diálogo do Passo a Passo, ou menu
  **Arquivo > Exportar passo a passo (PDF)...**. O nome sugerido é
  `<circuito>-passo-a-passo.pdf`.
- **No CLI:** `--pdf <ficheiro>` combinado com `--solve` (gera o PDF do
  circuito resolvido, com o desenho renderizado; pode combinar-se com
  `--passo-a-passo`, `--csv` e `--report`).

## Componentes suportados (análise DC)

| Componente | Comportamento em DC |
|---|---|
| Resistor | `I = (Va - Vb) / R` |
| Fonte de tensão | `V(A) - V(B) = valor` (ramo MNA) |
| Fonte de corrente | Corrente fixa de A para B |
| Capacitor | Circuito aberto (corrente zero) |
| Indutor | Curto-circuito (fonte de 0 V) |
| Fio | Curto-circuito (fonte de 0 V) |
| Terra | Curto entre o nó e o terra (0 V) |

Erros detetados com mensagem clara em PT-BR: circuito vazio, circuito sem
terra, fonte de tensão em curto, malha de fontes de tensão inconsistente,
nó flutuante e sistema singular.

## Exemplos (valores esperados)

| Exemplo | Resultado esperado |
|---|---|
| `divisor-tensao.json` — 12 V, R1 1 kΩ + R2 2 kΩ | Nó do meio: **8,00 V**; corrente: **4,00 mA** |
| `ponte-wheatstone.json` — 12 V, 4× 1 kΩ equilibrada | Nós do meio: **6,00 V**; corrente no ramo central: **~0 A** |
| `serie.json` — 12 V, 1 kΩ + 2 kΩ + 3 kΩ em série | Corrente: **2,00 mA**; nós intermédios: **10,00 V** e **6,00 V**; quedas: 2 V / 4 V / 6 V |
| `paralelo.json` — 12 V, 1 kΩ // 2 kΩ | Tensão: **12,00 V**; correntes: **12 mA** e **6 mA** |
| `delta-estrela.json` — 12 V, série 10 Ω, delta 30 Ω, carga 15 Ω | Corrente total: **0,2667 A**; VA: **9,333 V**; VB: **6,667 V**; VC: **4,000 V**; R_Y = **10 Ω** por ramo |
| `fonte-corrente.json` — 10 mA // 1 kΩ | Tensão: **10,00 V**; corrente no resistor: **10 mA** |
| `circuito-lucas.json` — 10 V + 12 V, 20 Ω / 2 Ω / 9 Ω, fios ao terra | Nó do meio: **2,773 V**; correntes: **0,361 A**, **1,387 A** e **1,025 A**; N0, N4, N6 e N8 são o mesmo ponto (terra) |

## Arquitetura

```
src/main/java/com/circuitolab/
  model/    Circuit, Node, Component, Resistor, VoltageSource,
            CurrentSource, Capacitor, Inductor, Wire, Ground
  solver/   MnaSolver (MNA), MatrixSolver (Gauss + pivotamento parcial,
            com rasto solveWithTrace), StepByStep (passo a passo
            explicado, secções 0-7; versão simples formatSimple),
            NetMerger (redes: nós que são o mesmo ponto elétrico),
            Solution, SolverException
  io/       CircuitFileIO (JSON manual, sem libs), JsonParser,
            CsvExporter, ReportExporter (inclui o passo a passo),
            PdfExporter (PDF à mão: A4, Helvetica, imagem FlateDecode)
  ui/       MainFrame, CircuitCanvas, ResultsPanel, Examples,
            StepByStepDialog
  util/     ValueParser (valores "2k", "1,5", "10m" e formatação
            amigável com unidades)
  app/      Main (GUI ou CLI --solve [--passo-a-passo | --steps]
            [--passo-a-passo-completo | --completo] [--pdf])
src/test/java/com/circuitolab/
  SolverTest, TestRunner (runner próprio, sem JUnit) — 21 testes
examples/   divisor-tensao.json, ponte-wheatstone.json, serie.json,
            paralelo.json, delta-estrela.json, fonte-corrente.json
```

O solver monta o sistema MNA (nós + ramos de tensão), resolve por eliminação
de Gauss com pivotamento parcial e devolve tensões, correntes (A → B) e
potência absorvida (`P = (Va - Vb) · I`, negativa quando o componente
fornece potência).

## Roadmap

- [ ] Análise AC (fasores) e transitória (integração no tempo)
- [ ] Fonte de tensão/corrente dependente
- [ ] Gráficos de tensão/corrente ao longo do tempo
- [ ] Deteção de curto com destaque visual do ramo em falta
- [ ] Exportar o esquema como imagem (PNG/SVG)
- [ ] Componentes com tolerância e varredura de valores (sweep)

## Licença

MIT — ver `LICENSE`. Autor: Lucas Welter Bohrer.
