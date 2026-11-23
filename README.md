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
./run-tests.sh    # corre os testes do solver
```

No Windows: `run.bat` (JDK 21 no `PATH`).

Modo CLI (sem GUI):

```sh
./build.sh
java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json
java -cp out com.circuitolab.app.Main --solve examples/ponte-wheatstone.json --csv out.csv --report out.txt
# Passo a passo completo no terminal (ver abaixo):
java -cp out com.circuitolab.app.Main --solve examples/divisor-tensao.json --passo-a-passo
java -cp out com.circuitolab.app.Main --solve examples/delta-estrela.json --steps
```

## Como usar (GUI)

1. Escolha uma ferramenta na barra superior: Resistor, Fonte V, Fonte I,
   Capacitor, Indutor, Fio, Terra, Mover ou Apagar.
2. **Clique-arraste** no canvas para desenhar o componente entre dois pontos
   da grade (snap de 20 px; clicar a até 12 px de um nó reutiliza esse nó).
   Um clique simples sem arrastar não cria nada — nem nó, nem componente.
3. **Duplo clique** num componente edita o valor (ex.: `2000` para 2 kΩ),
   sem criar nós soltos.
4. **Resolver (Ctrl+R)** roda o solver: as tensões aparecem junto aos nós,
   as correntes junto aos componentes, e as tabelas no painel lateral.
5. Menu **Arquivo**: Novo / Abrir / Salvar (JSON próprio) / Exportar CSV / TXT
   / **Limpar nós soltos** (remove nós sem componente ligado).
   Menu **Exemplos**: Divisor de Tensão, Ponte de Wheatstone, Série,
   Paralelo, Delta-Estrela.
6. **Terra é obrigatória:** pelo menos um componente tem de tocar o nó terra
   (use a ferramenta Terra ou ligue um componente ao símbolo de terra).
   Sem terra, o solver recusa-se a resolver e explica porquê.

## Passo a Passo (o cálculo, conforme o circuito)

O botão **Passo a Passo** (na barra de ferramentas) e o menu
**Exibir > Passo a Passo...** resolvem o circuito atual e abrem um diálogo
com o cálculo completo, que também pode ser copiado:

1. **Incógnitas** — `V(N1)`, `V(N2)`, `I(V1)`, ...
2. **Equações** — LKC em cada nó, com os termos `(Va - Vb) / R` e as
   equações dos ramos de tensão
3. **Sistema A · x = b** — a matriz aumentada `[A | b]`
4. **Eliminação de Gauss** — a matriz após cada coluna (com pivotamento
   parcial e troca de linhas quando houver) e os fatores usados
5. **Retrosubstituição** — cada incógnita, da última para a primeira
6. **Resultado final** — tensões, correntes (com a fórmula de cada uma)
   e potências
7. **Conversão Delta→Estrela** — só quando o circuito tem um triângulo
   de resistores: as fórmulas `R_Y = R·R / (soma dos 3 R)`, os valores
   e a tensão do centro da estrela

No CLI, a mesma saída sai no terminal com `--passo-a-passo` (ou `--steps`),
e o relatório TXT (`--report` / Exportar relatório) inclui o passo a passo
no fim do ficheiro.

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

## Arquitetura

```
src/main/java/com/circuitolab/
  model/    Circuit, Node, Component, Resistor, VoltageSource,
            CurrentSource, Capacitor, Inductor, Wire, Ground
  solver/   MnaSolver (MNA), MatrixSolver (Gauss + pivotamento parcial,
            com rasto solveWithTrace), StepByStep (passo a passo),
            Solution, SolverException
  io/       CircuitFileIO (JSON manual, sem libs), JsonParser,
            CsvExporter, ReportExporter (inclui o passo a passo)
  ui/       MainFrame, CircuitCanvas, ResultsPanel, Examples,
            StepByStepDialog
  app/      Main (GUI ou CLI --solve [--passo-a-passo | --steps])
src/test/java/com/circuitolab/
  SolverTest, TestRunner (runner próprio, sem JUnit) — 15 testes
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
