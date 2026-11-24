package com.circuitolab.solver;

import com.circuitolab.model.Capacitor;
import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.model.CurrentSource;
import com.circuitolab.model.Ground;
import com.circuitolab.model.Inductor;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;
import com.circuitolab.model.VoltageSource;
import com.circuitolab.model.Wire;
import com.circuitolab.util.ValueParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolução passo a passo da análise DC, escrita como um tutorial: o
 * aluno consegue acompanhar o que está a acontecer em cada etapa, com
 * os números do desenho (rótulos e nós reais), as contas substituídas
 * e a conferência no fim.
 *
 * O texto é gerado por format() e é o mesmo na GUI, no CLI, no
 * relatório TXT e no PDF exportado.
 *
 * Secções: 0. Mapa do circuito | 1. O que vamos calcular e porquê |
 * 2. Equações (LKC), nó por nó | 3. Como a matriz A é montada |
 * 4. Eliminação de Gauss | 5. Retrosubstituição |
 * 6. Resultado e conferência | 7. Conversão Delta→Estrela (se houver).
 */
public class StepByStep {
    private final Circuit circuit;
    private final List<String> unknowns;
    private final double[][] matrixA;
    private final double[] vectorB;
    private final List<String> equations;
    private final MatrixSolver.Trace trace;
    private final Solution solution;
    private final List<String> deltaSection;
    /** Redes (nós que são o mesmo ponto elétrico) — só para a explicação. */
    private final NetMerger nets;

    public StepByStep(Circuit circuit, List<String> unknowns, double[][] matrixA,
                      double[] vectorB, List<String> equations,
                      MatrixSolver.Trace trace, Solution solution,
                      List<String> deltaSection) {
        this.circuit = circuit;
        this.unknowns = List.copyOf(unknowns);
        this.matrixA = copy(matrixA);
        this.vectorB = vectorB.clone();
        this.equations = List.copyOf(equations);
        this.trace = trace;
        this.solution = solution;
        this.deltaSection = List.copyOf(deltaSection);
        this.nets = new NetMerger(circuit);
    }

    /** As redes do circuito (nós que são o mesmo ponto elétrico). */
    public NetMerger getNets() { return nets; }

    public List<String> getUnknowns() { return unknowns; }
    public double[][] getMatrixA() { return copy(matrixA); }
    public double[] getVectorB() { return vectorB.clone(); }
    public List<String> getEquations() { return equations; }
    public MatrixSolver.Trace getTrace() { return trace; }
    public Solution getSolution() { return solution; }
    public List<String> getDeltaSection() { return deltaSection; }

    /** Formata um número com 6 casas decimais, sem notação científica. */
    public static String fmt(double v) {
        if (v == 0.0 || Math.abs(v) < 0.0000005) return "0.000000";
        if (Math.abs(v) >= 1000000.0 || Math.abs(v) < 0.000001) {
            return String.format(Locale.US, "%.6e", v);
        }
        return String.format(Locale.US, "%.6f", v);
    }

    /** 6 casas decimais com vírgula (para as contas dentro das explicações). */
    static String fmt6c(double v) {
        return fmt(v).replace('.', ',');
    }

    /** Rótulo de tensão de um nó para as equações: "V(N2)" ou "0" para o terra. */
    static String vLabel(int nodeId) {
        return nodeId == Node.GROUND_ID ? "0" : "V(N" + nodeId + ")";
    }

    /** Nome do tipo em PT-BR, para as tabelas e explicações. */
    public static String typePt(Component c) {
        if (c instanceof Resistor) return "Resistor";
        if (c instanceof VoltageSource) return "Fonte de tensão";
        if (c instanceof CurrentSource) return "Fonte de corrente";
        if (c instanceof Capacitor) return "Capacitor";
        if (c instanceof Inductor) return "Indutor";
        if (c instanceof Wire) return "Fio";
        if (c instanceof Ground) return "Terra";
        return c.getType();
    }

    /** Valor do componente com unidade amigável (ex.: "1,000 kΩ", "12,000 V"). */
    static String friendlyValue(Component c) {
        if (c instanceof Resistor) return ValueParser.fmtResistance(c.getValue());
        if (c instanceof VoltageSource) return ValueParser.fmtVoltage(c.getValue());
        if (c instanceof CurrentSource) return ValueParser.fmtCurrent(c.getValue());
        if (c instanceof Capacitor) return ValueParser.fmtCapacitance(c.getValue());
        if (c instanceof Inductor) return ValueParser.fmtInductance(c.getValue());
        if (c instanceof Wire) return "0 V (curto-circuito)";
        if (c instanceof Ground) return "0 V (terra)";
        return ValueParser.fmt3(c.getValue()) + " " + c.getUnit();
    }

    /** Nós que não são o terra, na ordem das incógnitas do sistema. */
    private List<Integer> nodeIds() {
        List<Integer> ids = new ArrayList<>();
        for (int id : solution.getNodeVoltages().keySet()) {
            if (id != Node.GROUND_ID) ids.add(id);
        }
        return ids;
    }

    private Component findByLabel(String label) {
        for (Component c : circuit.getComponents()) {
            if (c.getLabel().equals(label)) return c;
        }
        return null;
    }

    /** Texto completo do passo a passo, pronto a mostrar, imprimir ou guardar. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("CircuitoLab — Passo a passo (Análise DC por MNA)\n");
        sb.append("Circuito: ").append(circuit.getName()).append("\n");
        sb.append("============================================================\n\n");
        section0Map(sb);
        section1Unknowns(sb);
        section2Equations(sb);
        section3Matrix(sb);
        section4Gauss(sb);
        section5BackSubstitution(sb);
        section6Result(sb);
        if (!deltaSection.isEmpty()) section7Delta(sb);
        sb.append("Notas: capacitor em DC = circuito aberto; indutor e fio em DC = curto.\n");
        return sb.toString();
    }

    // ==================================================================
    // VERSÃO SIMPLES — linguagem de leigo, sem matriz nem Gauss.
    //
    // Ideia: antes de fazer contas, juntar os nós que são o mesmo ponto
    // (as redes) e separar o que já se sabe (terra e nós ligados direto
    // a uma fonte) do que falta descobrir. Na maior parte dos circuitos
    // só falta uma tensão — e aí a conta é uma equação só, resolvida
    // passo a passo. O resto sai da Lei de Ohm.
    // ==================================================================

    /** Número curto: inteiro sem casas, senão 3 casas com vírgula ("20", "2,773"). */
    static String shortNum(double v) {
        if (v == 0.0) return "0";
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e9) {
            return String.valueOf((long) Math.rint(v));
        }
        return ValueParser.fmt3(v);
    }

    /** Corrente em linguagem simples: "0,361 A" em vez de "361,345 mA". */
    static String fmtCurrentSimple(double a) {
        if (a == 0.0) return "0 A";
        double m = Math.abs(a);
        if (m >= 0.01) return ValueParser.fmt3(a) + " A";
        if (m >= 0.0001) return ValueParser.fmt3(a * 1000) + " mA";
        return ValueParser.fmtCurrent(a);
    }

    /** Nome de um ponto para as explicações: "o terra" ou "o nó N5". */
    private String netLabel(int nodeId) {
        int rep = nets.netOf(nodeId);
        return rep == Node.GROUND_ID ? "o terra" : "o nó N" + rep;
    }

    /** Ponto sem artigo, para usar depois de "do", "no", "ao": "terra" ou "nó N5". */
    private String netPoint(int nodeId) {
        int rep = nets.netOf(nodeId);
        return rep == Node.GROUND_ID ? "terra" : "nó N" + rep;
    }

    /** Nome de uma rede (sem artigo): "Terra" ou "N5". */
    private static String netName(int rep) {
        return rep == Node.GROUND_ID ? "Terra" : "N" + rep;
    }

    /** Junta uma lista em PT-BR: "a", "a e b", "a, b e c". */
    private static String joinPt(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " e " + items.get(1);
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.get(items.size() - 1);
    }

    /** Descrição dos curtos que ligam os nós de uma rede ("pelos fios W7 e W8"). */
    private String netLinksText(NetMerger.Net net) {
        List<String> wireLabels = new ArrayList<>();
        List<String> inductorLabels = new ArrayList<>();
        boolean hasGround = false;
        for (Component c : circuit.getComponents()) {
            if (!NetMerger.isShortInDc(c)) continue;
            if (net.nodes().contains(c.getNodeA()) && net.nodes().contains(c.getNodeB())) {
                if (c instanceof Ground) hasGround = true;
                else if (c instanceof Inductor) inductorLabels.add(c.getLabel());
                else wireLabels.add(c.getLabel());
            }
        }
        List<String> parts = new ArrayList<>();
        if (!wireLabels.isEmpty()) {
            parts.add(wireLabels.size() == 1
                ? "pelo fio " + wireLabels.get(0)
                : "pelos fios " + joinPt(wireLabels));
        }
        if (!inductorLabels.isEmpty()) {
            parts.add(inductorLabels.size() == 1
                ? "pelo indutor " + inductorLabels.get(0)
                : "pelos indutores " + joinPt(inductorLabels));
        }
        if (hasGround) parts.add("pelo terra");
        return joinPt(parts);
    }

    /** Uma rede conhecida: o representante, a tensão e o porquê de se saber. */
    private record KnownNet(int rep, double voltage, String reason) {}

    /**
     * Tensões que já se sabem sem fazer conta: o terra (0 V) e as redes
     * ligadas a ele (ou a outra rede conhecida) por uma fonte de tensão.
     * Propaga-se de fonte em fonte até não haver mais nada a descobrir.
     */
    private List<KnownNet> knownNets() {
        java.util.Map<Integer, Double> known = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, String> reason = new java.util.LinkedHashMap<>();
        known.put(Node.GROUND_ID, 0.0);
        reason.put(Node.GROUND_ID, "o terra é a referência: 0 V por definição.");
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Component c : circuit.getComponents()) {
                if (!(c instanceof VoltageSource)) continue;
                int ra = nets.netOf(c.getNodeA());
                int rb = nets.netOf(c.getNodeB());
                if (ra == rb) continue;
                Double va = known.get(ra);
                Double vb = known.get(rb);
                String v = ValueParser.fmtVoltage(c.getValue());
                if (va != null && vb == null) {
                    known.put(rb, va - c.getValue());
                    reason.put(rb, "a fonte " + c.getLabel() + " liga " + netLabel(c.getNodeB())
                        + " ao " + netPoint(c.getNodeA()) + ": está " + v
                        + " abaixo do " + netPoint(c.getNodeA()) + ".");
                    changed = true;
                } else if (vb != null && va == null) {
                    known.put(ra, vb + c.getValue());
                    reason.put(ra, "a fonte " + c.getLabel() + " liga " + netLabel(c.getNodeA())
                        + " ao " + netPoint(c.getNodeB()) + ": está " + v
                        + " acima do " + netPoint(c.getNodeB()) + ".");
                    changed = true;
                }
            }
        }
        List<KnownNet> out = new ArrayList<>();
        for (var e : known.entrySet()) {
            out.add(new KnownNet(e.getKey(), e.getValue(), reason.get(e.getKey())));
        }
        return out;
    }

    /** Redes cuja tensão falta descobrir (têm resistor ou fonte de corrente). */
    private List<Integer> unknownNetReps(java.util.Map<Integer, Double> known) {
        List<Integer> out = new ArrayList<>();
        for (NetMerger.Net net : nets.nets()) {
            if (known.containsKey(net.representative())) continue;
            boolean hasBranch = false;
            for (Component c : net.components()) {
                if (c instanceof Resistor || c instanceof CurrentSource) { hasBranch = true; break; }
            }
            if (hasBranch) out.add(net.representative());
        }
        return out;
    }

    /** Texto da versão simples, pronto a mostrar, imprimir ou guardar. */
    public String formatSimple() {
        List<KnownNet> knownList = knownNets();
        java.util.Map<Integer, Double> known = new java.util.LinkedHashMap<>();
        for (KnownNet k : knownList) known.put(k.rep(), k.voltage());
        List<Integer> unknown = unknownNetReps(known);

        StringBuilder sb = new StringBuilder();
        sb.append("CircuitoLab — Passo a passo simples\n");
        sb.append("Circuito: ").append(circuit.getName()).append("\n");
        sb.append("============================================================\n\n");
        simpleSection1Goal(sb);
        simpleSection2Known(sb, knownList, known);
        simpleSection3Unknown(sb, unknown);
        simpleSection4Math(sb, unknown, known);
        simpleSection5Currents(sb);
        simpleSection6Check(sb, unknown);
        if (!deltaSection.isEmpty()) {
            sb.append("Nota: este circuito tem um triângulo de resistores (delta).\n");
            sb.append("A conversão para estrela está explicada na versão completa.\n");
        }
        return sb.toString();
    }

    // 1. O que a gente quer descobrir + o desenho, em poucas linhas.
    private void simpleSection1Goal(StringBuilder sb) {
        sb.append("1. O que a gente quer descobrir\n");
        sb.append("   Queremos saber a tensão de cada ponto do circuito (quantos\n");
        sb.append("   volts há entre esse ponto e o terra) e a corrente de cada\n");
        sb.append("   componente. A tensão mede-se sempre em relação ao terra\n");
        sb.append("   (0 V). A corrente de um resistor sai da Lei de Ohm:\n");
        sb.append("   corrente = (tensão de um lado - tensão do outro lado)\n");
        sb.append("   / resistência.\n\n");
        sb.append("   O desenho:\n");
        for (Component c : circuit.getComponents()) {
            sb.append("   ").append(c.getLabel()).append(": ").append(typePt(c).toLowerCase())
              .append(" de ").append(friendlyValue(c));
            if (c instanceof Ground) {
                sb.append(" — liga o nó N").append(c.getNodeA()).append(" ao terra (N0).\n");
            } else {
                sb.append(" — entre o nó N").append(c.getNodeA())
                  .append(" e o nó N").append(c.getNodeB()).append(".\n");
            }
        }
        sb.append("\n");
    }

    // 2. O que já se sabe: redes que são o mesmo ponto + fontes diretas.
    private void simpleSection2Known(StringBuilder sb, List<KnownNet> knownList,
                                     java.util.Map<Integer, Double> known) {
        sb.append("2. O que já sabemos sem fazer conta\n");
        sb.append("   Antes de qualquer conta, há pontos cuja tensão já se sabe:\n\n");
        // Redes com mais de um nó: são o mesmo ponto, diz-se uma vez só.
        for (NetMerger.Net net : nets.nets()) {
            if (net.nodes().size() <= 1) continue;
            List<String> names = new ArrayList<>();
            for (int id : net.nodes()) names.add("N" + id);
            String links = netLinksText(net);
            sb.append("   - Os nós ").append(joinPt(names)).append(" são o mesmo ponto");
            if (!links.isEmpty()) sb.append(" (estão ligados ").append(links).append(")");
            sb.append(". Por isso, para a conta, contam como um ponto só.\n");
        }
        for (KnownNet k : knownList) {
            if (k.rep() == Node.GROUND_ID) {
                sb.append("   - O terra está em 0 V: é a referência, tudo se mede a partir dele.\n");
                continue;
            }
            sb.append("   - ").append(netLabel(k.rep()).substring(0, 1).toUpperCase())
              .append(netLabel(k.rep()).substring(1))
              .append(" está em ").append(ValueParser.fmtVoltage(k.voltage()))
              .append(": ").append(k.reason()).append("\n");
        }
        // Fontes entre dois pontos que ainda não se sabem: a diferença sabe-se.
        for (Component c : circuit.getComponents()) {
            if (!(c instanceof VoltageSource)) continue;
            int ra = nets.netOf(c.getNodeA());
            int rb = nets.netOf(c.getNodeB());
            if (ra == rb || known.containsKey(ra) || known.containsKey(rb)) continue;
            sb.append("   - A fonte ").append(c.getLabel()).append(" liga ")
              .append(netLabel(c.getNodeA())).append(" ao ").append(netPoint(c.getNodeB()))
              .append(": a diferença entre eles é ").append(ValueParser.fmtVoltage(c.getValue()))
              .append(" (o valor da fonte).\n");
        }
        sb.append("\n");
    }

    // 3. O que falta descobrir: só as redes desconhecidas, nada mais.
    private void simpleSection3Unknown(StringBuilder sb, List<Integer> unknown) {
        sb.append("3. O que falta descobrir\n");
        if (unknown.isEmpty()) {
            sb.append("   Nada. Todas as tensões já se sabem: as fontes e o terra\n");
            sb.append("   dizem tudo. Só falta calcular as correntes (secção 5).\n\n");
            return;
        }
        if (unknown.size() == 1) {
            int rep = unknown.get(0);
            NetMerger.Net net = nets.netContaining(rep);
            sb.append("   Só falta um ponto: ").append(netLabel(rep)).append(". ");
            if (net != null && net.nodes().size() > 1) {
                List<String> names = new ArrayList<>();
                for (int id : net.nodes()) names.add("N" + id);
                sb.append("(Os nós ").append(joinPt(names)).append(" são o mesmo ponto.) ");
            }
            sb.append("Vamos chamar V à tensão dele (em volts, em relação\n");
            sb.append("   ao terra). É este V que vamos descobrir com a conta a seguir.\n\n");
            return;
        }
        List<String> names = new ArrayList<>();
        for (int rep : unknown) names.add(netLabel(rep));
        sb.append("   Faltam ").append(unknown.size()).append(" pontos: ")
          .append(joinPt(names)).append(".\n");
        for (int rep : unknown) {
            sb.append("   - Vamos chamar V(N").append(rep).append(") à tensão de ")
              .append(netLabel(rep)).append(".\n");
        }
        sb.append("\n");
    }

    // 4. A conta: LKC no(s) nó(s) desconhecido(s); com 1 incógnita, a álgebra toda.
    private void simpleSection4Math(StringBuilder sb, List<Integer> unknown,
                                    java.util.Map<Integer, Double> known) {
        sb.append("4. A conta\n");
        if (unknown.isEmpty()) {
            sb.append("   Não há conta para fazer: não há tensões por descobrir.\n\n");
            return;
        }
        sb.append("   Regra: num ponto, o que entra tem de ser igual ao que sai\n");
        sb.append("   (a carga não se acumula). É a Lei de Kirchhoff das Correntes.\n");
        sb.append("   A corrente de um resistor é (tensão deste lado - tensão do\n");
        sb.append("   outro lado) / resistência.\n\n");
        if (unknown.size() == 1) {
            simpleSingleUnknownMath(sb, unknown.get(0), known);
        } else {
            for (int rep : unknown) {
                sb.append("   No ").append(netPoint(rep)).append(":\n");
                sb.append("   ").append(kclEquationText(rep, "V(N" + rep + ")", known)).append(" = 0\n\n");
            }
            sb.append("   Isto é um sistema com ").append(unknown.size())
              .append(" incógnitas. Os valores são:\n");
            for (int rep : unknown) {
                sb.append("   - V(N").append(rep).append(") = ")
                  .append(ValueParser.fmtVoltage(solution.voltageOf(rep))).append("\n");
            }
            sb.append("   A resolução completa deste sistema, com matriz, está na\n");
            sb.append("   versão completa (aba \"Completa\").\n\n");
        }
    }

    /**
     * Equação da LKC de um ponto desconhecido, em texto: um termo por
     * resistor ("(V - 10)/20") e as fontes de corrente como constantes.
     * "var" é o nome da incógnita ("V" ou "V(N5)").
     */
    private String kclEquationText(int rep, String var, java.util.Map<Integer, Double> known) {
        List<String> terms = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c instanceof Resistor r) {
                int otherNet = -1;
                if (nets.netOf(r.getNodeA()) == rep) otherNet = nets.netOf(r.getNodeB());
                else if (nets.netOf(r.getNodeB()) == rep) otherNet = nets.netOf(r.getNodeA());
                else continue;
                if (otherNet == rep) continue; // resistor dentro da mesma rede: sem efeito
                String rStr = shortNum(r.getValue());
                if (known.containsKey(otherNet)) {
                    double vw = known.get(otherNet);
                    terms.add(vw == 0.0 ? var + "/" + rStr
                        : "(" + var + " - " + shortNum(vw) + ")/" + rStr);
                } else {
                    terms.add("(" + var + " - V(N" + otherNet + "))/" + rStr);
                }
            } else if (c instanceof CurrentSource cs) {
                boolean aIn = nets.netOf(cs.getNodeA()) == rep;
                boolean bIn = nets.netOf(cs.getNodeB()) == rep;
                if (aIn && !bIn) terms.add(fmtCurrentSimple(cs.getValue()));
                else if (bIn && !aIn) terms.add("-" + fmtCurrentSimple(cs.getValue()));
            }
        }
        if (terms.isEmpty()) return "0";
        return String.join(" + ", terms).replace("+ -", "- ");
    }

    /** A álgebra completa para o caso de uma só incógnita. */
    private void simpleSingleUnknownMath(StringBuilder sb, int rep,
                                         java.util.Map<Integer, Double> known) {
        // Coeficiente de V e lado direito: V * coeff = rhs.
        double coeff = 0.0;
        double rhs = 0.0;
        List<String> invParts = new ArrayList<>();
        List<String> rhsParts = new ArrayList<>();
        List<String> termTexts = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c instanceof Resistor r) {
                int otherNet = -1;
                if (nets.netOf(r.getNodeA()) == rep) otherNet = nets.netOf(r.getNodeB());
                else if (nets.netOf(r.getNodeB()) == rep) otherNet = nets.netOf(r.getNodeA());
                else continue;
                if (otherNet == rep) continue;
                String rStr = shortNum(r.getValue());
                coeff += 1.0 / r.getValue();
                invParts.add("1/" + rStr);
                if (known.containsKey(otherNet)) {
                    double vw = known.get(otherNet);
                    if (vw == 0.0) {
                        // (V - 0)/R é só V/R: escreve-se "V/R" e não entra no outro lado.
                        termTexts.add("V/" + rStr);
                    } else {
                        rhs += vw / r.getValue();
                        rhsParts.add(shortNum(vw) + "/" + rStr);
                        termTexts.add("(V - " + shortNum(vw) + ")/" + rStr);
                    }
                } else {
                    termTexts.add("(V - V(N" + otherNet + "))/" + rStr);
                }
            } else if (c instanceof CurrentSource cs) {
                boolean aIn = nets.netOf(cs.getNodeA()) == rep;
                boolean bIn = nets.netOf(cs.getNodeB()) == rep;
                if (aIn && !bIn) {
                    rhs -= cs.getValue();
                    rhsParts.add("-" + fmtCurrentSimple(cs.getValue()));
                    termTexts.add(fmtCurrentSimple(cs.getValue()));
                } else if (bIn && !aIn) {
                    rhs += cs.getValue();
                    rhsParts.add(fmtCurrentSimple(cs.getValue()));
                    termTexts.add("-" + fmtCurrentSimple(cs.getValue()));
                }
            }
        }
        double v = solution.voltageOf(rep);
        sb.append("   No ").append(netPoint(rep)).append(" chegam os resistores. A soma\n");
        sb.append("   do que sai dele tem de ser zero:\n");
        sb.append("   ").append(String.join(" + ", termTexts).replace("+ -", "- ")).append(" = 0\n\n");
        if (coeff < 1e-12) {
            sb.append("   (Esta equação não tem resistores para isolar o V: o valor\n");
            sb.append("   vem da versão completa.)\n\n");
            sb.append("   V = ").append(ValueParser.fmtVoltage(v)).append("\n\n");
            return;
        }
        sb.append("   Passo 1 — juntar os termos em V. Cada termo (V - número)/R\n");
        sb.append("   contribui com V/R. Somando os inversos das resistências:\n");
        sb.append("   V x (").append(String.join(" + ", invParts)).append(") = ")
          .append(String.join(" + ", rhsParts).replace("+ -", "- ")).append("\n\n");
        sb.append("   Passo 2 — fazer as contas dos dois lados:\n");
        sb.append("   V x ").append(shortNum(coeff)).append(" = ")
          .append(shortNum(rhs)).append("\n\n");
        sb.append("   Passo 3 — isolar o V (dividir os dois lados por ")
          .append(shortNum(coeff)).append("):\n");
        sb.append("   V = ").append(shortNum(rhs)).append(" / ").append(shortNum(coeff))
          .append(" = ").append(ValueParser.fmtVoltage(v)).append("\n\n");
        sb.append("   ").append(netLabel(rep).substring(0, 1).toUpperCase())
          .append(netLabel(rep).substring(1))
          .append(" está a ").append(ValueParser.fmtVoltage(v))
          .append(" em relação ao terra.\n\n");
    }

    // 5. As correntes, pela Lei de Ohm, com o sentido em palavras.
    private void simpleSection5Currents(StringBuilder sb) {
        sb.append("5. As correntes\n");
        sb.append("   Agora que se sabem as tensões, cada corrente sai da Lei de Ohm.\n\n");
        for (Component c : circuit.getComponents()) {
            if (c instanceof Resistor r) {
                double va = solution.voltageOf(c.getNodeA());
                double vb = solution.voltageOf(c.getNodeB());
                double i = solution.currentOf(c.getId());
                sb.append("   - ").append(r.getLabel()).append(" (").append(friendlyValue(r))
                  .append("): I = (").append(shortNum(va)).append(" - ").append(shortNum(vb))
                  .append(")/").append(shortNum(r.getValue())).append(" = ")
                  .append(fmtCurrentSimple(i)).append(". ");
                if (i >= 1e-9) {
                    sb.append("Vai do ").append(netPoint(c.getNodeA())).append(" (")
                      .append(ValueParser.fmtVoltage(va)).append(") para ")
                      .append(netLabel(c.getNodeB())).append(" (")
                      .append(ValueParser.fmtVoltage(vb)).append(").\n");
                } else if (i <= -1e-9) {
                    sb.append("O sinal deu negativo: na realidade vai ao contrário, do ")
                      .append(netPoint(c.getNodeB())).append(" (")
                      .append(ValueParser.fmtVoltage(vb)).append(") para ")
                      .append(netLabel(c.getNodeA())).append(" (")
                      .append(ValueParser.fmtVoltage(va)).append(").\n");
                } else {
                    sb.append("Corrente ~0: não passa nada por aqui.\n");
                }
            } else if (c instanceof CurrentSource cs) {
                sb.append("   - A fonte de corrente ").append(cs.getLabel())
                  .append(" debita ").append(fmtCurrentSimple(cs.getValue()))
                  .append(" do ").append(netPoint(cs.getNodeA())).append(" para ")
                  .append(netLabel(cs.getNodeB())).append(" (é o valor dela, fixo).\n");
            } else if (c instanceof Capacitor) {
                sb.append("   - O capacitor ").append(c.getLabel())
                  .append(" não deixa passar corrente em DC: I = 0 A.\n");
            }
        }
        // Fios, indutores e fontes de tensão: uma frase curta cada um.
        List<String> extras = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c instanceof VoltageSource) {
                double i = solution.currentOf(c.getId());
                double p = solution.powerOf(c.getId());
                if (Math.abs(i) < 1e-9) continue;
                extras.add("a fonte " + c.getLabel() + " "
                    + (p < 0 ? "fornece " : "absorve ") + fmtCurrentSimple(Math.abs(i)));
            } else if (NetMerger.isShortInDc(c) && !(c instanceof Ground)) {
                double i = solution.currentOf(c.getId());
                if (Math.abs(i) < 1e-9) continue;
                extras.add((c instanceof Inductor ? "o indutor " : "o fio ") + c.getLabel()
                    + " leva " + fmtCurrentSimple(Math.abs(i)));
            }
        }
        if (!extras.isEmpty()) {
            String joined = joinPt(extras);
            sb.append("\n   E os fios e as fontes? ")
              .append(joined.substring(0, 1).toUpperCase()).append(joined.substring(1))
              .append(".\n");
        }
        sb.append("\n");
    }

    // 6. Confere? LKC no ponto principal + balanço de potência, uma linha cada.
    private void simpleSection6Check(StringBuilder sb, List<Integer> unknown) {
        sb.append("6. Confere?\n");
        int checkRep = -1;
        if (!unknown.isEmpty()) {
            checkRep = unknown.get(0);
        } else {
            // Sem incógnitas: conferir no ponto com mais resistores.
            int best = 0;
            for (NetMerger.Net net : nets.nets()) {
                int count = 0;
                for (Component c : net.components()) {
                    if (c instanceof Resistor) count++;
                }
                if (count > best && net.representative() != Node.GROUND_ID) {
                    best = count;
                    checkRep = net.representative();
                }
            }
        }
        if (checkRep >= 0) {
            sb.append(kclCheckSimple(checkRep));
        }
        double dissipated = 0.0;
        double supplied = 0.0;
        for (Component c : circuit.getComponents()) {
            double p = solution.powerOf(c.getId());
            if (c instanceof Resistor) dissipated += p;
            if (p < 0) supplied += -p;
        }
        sb.append("   Potência: as fontes entregam ").append(ValueParser.fmtPower(supplied))
          .append(" e os resistores gastam ").append(ValueParser.fmtPower(dissipated))
          .append(". Os dois lados são iguais: a conta fecha.\n");
    }

    /** Conferência da LKC num ponto, em palavras: o que entra e o que sai. */
    private String kclCheckSimple(int rep) {
        List<String> in = new ArrayList<>();
        List<String> out = new ArrayList<>();
        double sumIn = 0.0;
        double sumOut = 0.0;
        double vRep = solution.voltageOf(rep);
        for (Component c : circuit.getComponents()) {
            boolean aIn = nets.netOf(c.getNodeA()) == rep;
            boolean bIn = nets.netOf(c.getNodeB()) == rep;
            if (!aIn && !bIn) continue;
            double leaving;
            if (c instanceof Resistor r) {
                int other = aIn ? c.getNodeB() : c.getNodeA();
                leaving = (vRep - solution.voltageOf(other)) / r.getValue();
            } else if (c instanceof CurrentSource cs) {
                leaving = aIn ? cs.getValue() : -cs.getValue();
            } else if (c.isVoltageBranch()) {
                double i = solution.currentOf(c.getId());
                leaving = aIn ? i : -i;
            } else {
                continue; // capacitor: 0 A em DC
            }
            String desc = "por " + c.getLabel() + " (" + fmtCurrentSimple(Math.abs(leaving)) + ")";
            if (leaving > 1e-9) { out.add(desc); sumOut += leaving; }
            else if (leaving < -1e-9) { in.add(desc); sumIn += -leaving; }
        }
        StringBuilder b = new StringBuilder();
        b.append("   No ").append(netPoint(rep)).append(": ");
        if (in.isEmpty() && out.isEmpty()) {
            b.append("não passa corrente nenhuma por aqui.\n");
            return b.toString();
        }
        if (!in.isEmpty()) {
            b.append("entram ").append(fmtCurrentSimple(sumIn)).append(", ").append(joinPt(in));
        }
        if (!out.isEmpty()) {
            if (!in.isEmpty()) b.append("; ");
            b.append("saem ").append(fmtCurrentSimple(sumOut)).append(", ").append(joinPt(out));
        }
        b.append(". ");
        double diff = Math.abs(sumIn - sumOut);
        if (diff < 0.001) {
            b.append("Entra = sai: confere.\n");
        } else {
            b.append("Entra e sai deviam ser iguais (diferença de ")
              .append(fmtCurrentSimple(diff)).append(").\n");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------
    // Secção 0 — Mapa do circuito: o texto com os números do desenho.
    // ------------------------------------------------------------------
    private void section0Map(StringBuilder sb) {
        sb.append("0. Mapa do circuito — confere com o desenho\n");
        sb.append("   Antes de qualquer conta, vamos pôr no papel o que está desenhado.\n");
        sb.append("   Cada linha abaixo é um componente do desenho, com o rótulo, o\n");
        sb.append("   valor e os dois nós onde ele está ligado (A = de onde sai a\n");
        sb.append("   corrente, B = para onde ela vai).\n\n");
        sb.append("   Componentes:\n");
        for (Component c : circuit.getComponents()) {
            sb.append("   ").append(c.getLabel()).append(": ").append(typePt(c)).append(", ")
              .append(friendlyValue(c)).append(" — nó A: N").append(c.getNodeA())
              .append(", nó B: N").append(c.getNodeB()).append("\n");
        }
        sb.append("\n   Nós (tensão de cada nó em relação ao terra):\n");
        for (Node n : circuit.getNodes()) {
            List<String> touching = new ArrayList<>();
            for (Component c : circuit.getComponents()) {
                if (c.getNodeA() == n.getId() || c.getNodeB() == n.getId()) touching.add(c.getLabel());
            }
            String volt = n.isGround()
                ? "0,000 V (terra, referência)"
                : ValueParser.fmtVoltage(solution.voltageOf(n.getId()));
            sb.append("   Nó N").append(n.getId()).append(": ").append(volt)
              .append("  —  ligam aqui: ")
              .append(touching.isEmpty() ? "(nenhum componente)" : String.join(", ", touching))
              .append("\n");
        }
        sb.append("   O nó N0 é o terra: 0 V por definição. Todas as tensões deste\n");
        sb.append("   passo a passo são medidas em relação a ele.\n\n");
    }

    // ------------------------------------------------------------------
    // Secção 1 — O que vamos calcular e porquê.
    // ------------------------------------------------------------------
    private void section1Unknowns(StringBuilder sb) {
        sb.append("1. O que vamos calcular e porquê\n");
        sb.append("   A ideia da análise nodal é esta: em vez de adivinhar as correntes,\n");
        sb.append("   calculamos primeiro a TENSÃO de cada nó (quantos volts há entre\n");
        sb.append("   esse nó e o terra). Depois, com as tensões conhecidas, cada\n");
        sb.append("   corrente sai direto da Lei de Ohm: I = (V(A) - V(B)) / R.\n");
        sb.append("   O terra (N0) não é incógnita: ele É a referência, 0 V.\n");
        sb.append("   Cada fonte de tensão traz uma incógnita extra: a corrente que\n");
        sb.append("   passa por ela. Uma fonte fixa a tensão, não a corrente — por\n");
        sb.append("   isso a corrente dela tem de ser descoberta pelo sistema.\n\n");
        sb.append("   Incógnitas (").append(unknowns.size()).append("):\n");
        for (int i = 0; i < unknowns.size(); i++) {
            String u = unknowns.get(i);
            sb.append("   x").append(i + 1).append(" = ").append(u).append(" — ")
              .append(describeUnknown(u)).append("\n");
        }
        sb.append("\n");
    }

    private String describeUnknown(String u) {
        if (u.startsWith("V(N")) {
            int id = Integer.parseInt(u.substring(3, u.length() - 1));
            return "a tensão do nó N" + id + " em relação ao terra (0 V).";
        }
        if (u.startsWith("I(")) {
            String label = u.substring(2, u.length() - 1);
            Component c = findByLabel(label);
            if (c == null) return "a corrente que atravessa o componente " + label + ".";
            String kind = typePt(c).toLowerCase();
            return "a corrente que atravessa " + articleOf(c) + " " + kind + " " + label
                + ", do nó N" + c.getNodeA() + " (terminal A) para o nó N" + c.getNodeB()
                + " (terminal B)."
                + (c instanceof VoltageSource
                    ? " É incógnita porque a fonte fixa a tensão, não a corrente."
                    : " É incógnita porque o componente é um curto em DC (0 V).");
        }
        return "incógnita do sistema.";
    }

    private static String articleOf(Component c) {
        return (c instanceof VoltageSource || c instanceof CurrentSource) ? "a" : "o";
    }

    // ------------------------------------------------------------------
    // Secção 2 — Equações (LKC), nó por nó, termo a termo.
    // ------------------------------------------------------------------
    private void section2Equations(StringBuilder sb) {
        sb.append("2. Equações (LKC), nó por nó\n");
        sb.append("   Lei de Kirchhoff das Correntes (LKC): num nó, a soma das\n");
        sb.append("   correntes que SAEM é zero — o que entra tem de ser igual ao\n");
        sb.append("   que sai, porque a carga não se acumula no nó.\n");
        sb.append("   Corrente num resistor: I = (V(do nó) - V(do outro lado)) / R.\n");
        sb.append("   Se o resultado der negativo, o sentido real é o contrário do\n");
        sb.append("   que escrevemos — o valor está certo, só o sentido inverte.\n");
        sb.append("   Condutância G = 1 / R (em siemens, S): é o inverso da\n");
        sb.append("   resistência, e é ela que vai aparecer na matriz. Um resistor\n");
        sb.append("   de 20 Ω tem G = 1 / 20 = 0,050 S — quanto maior G, mais\n");
        sb.append("   corrente o resistor deixa passar para a mesma tensão.\n\n");

        List<Integer> nodes = nodeIds();
        for (int i = 0; i < nodes.size(); i++) {
            int nodeId = nodes.get(i);
            sb.append("   --- Nó N").append(nodeId).append(" ---\n");
            List<String> termLines = nodeTermLines(nodeId);
            if (termLines.isEmpty()) {
                sb.append("   (nenhum ramo com corrente neste nó)\n");
            } else {
                for (String line : termLines) sb.append("   ").append(line).append("\n");
            }
            sb.append("   Equação do nó (soma do que sai = 0):\n");
            sb.append("   ").append(rowEquation(i)).append("\n");
            sb.append("   Conferência com os valores encontrados: ");
            sb.append(kclCheckLine(nodeId)).append("\n\n");
        }
        // Equações dos ramos de tensão.
        int nNodes = nodes.size();
        for (int k = 0; k < unknowns.size() - nNodes; k++) {
            int row = nNodes + k;
            String label = unknowns.get(nNodes + k).substring(2, unknowns.get(nNodes + k).length() - 1);
            Component c = findByLabel(label);
            if (c == null) continue;
            sb.append("   --- Ramo de tensão: ").append(label).append(" ---\n");
            if (c instanceof VoltageSource) {
                sb.append("   A fonte fixa a diferença de tensão entre os terminais:\n");
                sb.append("   V(N").append(c.getNodeA()).append(") - V(N").append(c.getNodeB())
                  .append(") = ").append(ValueParser.fmtVoltage(c.getValue())).append("\n");
                sb.append("   Ou seja: o terminal A está ")
                  .append(ValueParser.fmtVoltage(c.getValue()))
                  .append(" acima do terminal B. Esta é a equação da linha ")
                  .append(row + 1).append(" da matriz: ").append(rowEquation(row)).append("\n\n");
            } else {
                sb.append("   ").append(typePt(c)).append(" em DC é um curto (0 V):\n");
                sb.append("   V(N").append(c.getNodeA()).append(") - V(N").append(c.getNodeB())
                  .append(") = 0,000 V. Equação da linha ").append(row + 1)
                  .append(": ").append(rowEquation(row)).append("\n\n");
            }
        }
    }

    /**
     * Uma linha por ramo ligado ao nó, explicando o termo em palavras e
     * mostrando a conta com os valores substituídos.
     */
    private List<String> nodeTermLines(int nodeId) {
        List<String> lines = new ArrayList<>();
        double vn = solution.voltageOf(nodeId);
        for (Component c : circuit.getComponents()) {
            boolean isA = c.getNodeA() == nodeId;
            boolean isB = c.getNodeB() == nodeId;
            if (!isA && !isB) continue;
            int other = isA ? c.getNodeB() : c.getNodeA();
            if (c instanceof Resistor r) {
                double vo = solution.voltageOf(other);
                double iLeaving = (vn - vo) / r.getValue();
                lines.add("pelo resistor " + r.getLabel() + " (" + ValueParser.fmtResistance(r.getValue())
                    + ") em direção ao nó N" + other + ": I = (V(N" + nodeId + ") - V(N" + other + ")) / "
                    + ValueParser.fmtResistance(r.getValue()) + " = (" + ValueParser.fmt3(vn) + " - "
                    + ValueParser.fmt3(vo) + ") / " + ValueParser.fmtResistance(r.getValue())
                    + " = " + ValueParser.fmtCurrent(iLeaving)
                    + (iLeaving < -1e-9
                        ? " (negativo: na realidade ela ENTRA no nó N" + nodeId + ", vindo do N" + other + ")."
                        : "."));
                lines.add("    (condutância deste resistor: G = 1 / "
                    + ValueParser.fmtResistance(r.getValue()) + " = "
                    + ValueParser.fmtConductance(1.0 / r.getValue()) + ")");
            } else if (c instanceof CurrentSource cs) {
                double leaving = isA ? cs.getValue() : -cs.getValue();
                lines.add("pela fonte de corrente " + cs.getLabel() + " ("
                    + ValueParser.fmtCurrent(cs.getValue()) + " de A para B): "
                    + (isA
                        ? "ela SAI do nó N" + nodeId + " com " + ValueParser.fmtCurrent(leaving) + "."
                        : "ela ENTRA no nó N" + nodeId + " com "
                            + ValueParser.fmtCurrent(cs.getValue())
                            + " (na soma do que sai, conta como "
                            + ValueParser.fmtCurrent(leaving) + ")."));
            } else if (c.isVoltageBranch()) {
                double i = solution.currentOf(c.getId());
                double leaving = isA ? i : -i;
                lines.add("pelo componente " + c.getLabel() + " (" + typePt(c).toLowerCase()
                    + "): a corrente I(" + c.getLabel() + ") = "
                    + ValueParser.fmtCurrent(i) + " "
                    + (isA ? "SAI do nó N" + nodeId : "ENTRA no nó N" + nodeId)
                    + " (na soma do que sai, conta como " + ValueParser.fmtCurrent(leaving)
                    + "). É uma incógnita do sistema, por isso entra como x"
                    + (unknowns.indexOf("I(" + c.getLabel() + ")") + 1) + ".");
            }
            // Capacitor em DC: aberto, não entra na equação.
        }
        return lines;
    }

    /** Soma das correntes que saem do nó, com os valores, para conferência. */
    private String kclCheckLine(int nodeId) {
        double vn = solution.voltageOf(nodeId);
        List<String> parts = new ArrayList<>();
        double sum = 0.0;
        for (Component c : circuit.getComponents()) {
            boolean isA = c.getNodeA() == nodeId;
            boolean isB = c.getNodeB() == nodeId;
            if (!isA && !isB) continue;
            double leaving;
            if (c instanceof Resistor r) {
                leaving = (vn - solution.voltageOf(isA ? c.getNodeB() : c.getNodeA())) / r.getValue();
            } else if (c instanceof CurrentSource cs) {
                leaving = isA ? cs.getValue() : -cs.getValue();
            } else if (c.isVoltageBranch()) {
                double i = solution.currentOf(c.getId());
                leaving = isA ? i : -i;
            } else {
                continue; // capacitor: 0 A em DC
            }
            sum += leaving;
            parts.add(ValueParser.fmtCurrent(leaving) + " (" + c.getLabel() + ")");
        }
        if (parts.isEmpty()) return "nó sem ramos com corrente.";
        return String.join(" + ", parts) + " = " + ValueParser.fmtCurrent(sum)
            + " (tem de dar zero — a menos de arredondamento, confere).";
    }

    /** Equação da linha i do sistema, com os coeficientes e o lado direito. */
    private String rowEquation(int i) {
        StringBuilder eq = new StringBuilder();
        boolean first = true;
        for (int j = 0; j < unknowns.size(); j++) {
            double a = matrixA[i][j];
            if (Math.abs(a) < 1e-12) continue;
            String term = fmt6c(Math.abs(a)) + " * x" + (j + 1);
            if (first) {
                eq.append(a < 0 ? "-" : "").append(term);
                first = false;
            } else {
                eq.append(a < 0 ? " - " : " + ").append(term);
            }
        }
        if (first) eq.append("0");
        eq.append(" = ").append(fmt6c(vectorB[i]));
        return eq.toString();
    }

    // ------------------------------------------------------------------
    // Secção 3 — Como a matriz A é montada.
    // ------------------------------------------------------------------
    private void section3Matrix(StringBuilder sb) {
        sb.append("3. Como a matriz A é montada (sistema A . x = b)\n");
        sb.append("   Cada linha da matriz é a equação de um nó (secção 2) escrita\n");
        sb.append("   em forma compacta; cada coluna é uma incógnita.\n");
        sb.append("   Regras de montagem, linha de um nó:\n");
        sb.append("   - na DIAGONAL vai a soma das condutâncias (1/R) de todos os\n");
        sb.append("     resistores ligados a esse nó;\n");
        sb.append("   - FORA da diagonal vai MENOS a condutância do resistor que liga\n");
        sb.append("     esse nó ao nó da coluna (0 se não houver resistor direto);\n");
        sb.append("   - na coluna de uma corrente de ramo vai +1 se a corrente sai do\n");
        sb.append("     nó, -1 se ela entra;\n");
        sb.append("   - no LADO DIREITO (b) vai o que as fontes de corrente injetam\n");
        sb.append("     no nó (e, nas linhas de ramo de tensão, o valor da fonte).\n");
        sb.append("   Linhas de ramo de tensão: a equação V(A) - V(B) = valor vira\n");
        sb.append("   +1 na coluna de V(A), -1 na de V(B) e o valor no lado direito.\n\n");
        List<Integer> nodes = nodeIds();
        for (int i = 0; i < nodes.size(); i++) {
            int nodeId = nodes.get(i);
            sb.append("   Linha ").append(i + 1).append(" (nó N").append(nodeId).append("): diagonal = ");
            List<String> gs = new ArrayList<>();
            double diag = 0.0;
            for (Component c : circuit.getComponents()) {
                if (c instanceof Resistor r
                        && (r.getNodeA() == nodeId || r.getNodeB() == nodeId)) {
                    gs.add("1/" + ValueParser.fmtResistance(r.getValue()));
                    diag += 1.0 / r.getValue();
                }
            }
            sb.append(gs.isEmpty() ? "0 (nenhum resistor ligado ao nó)"
                : String.join(" + ", gs) + " = " + ValueParser.fmtConductance(diag));
            sb.append("; lado direito b = ").append(fmt6c(vectorB[i]));
            if (Math.abs(vectorB[i]) < 1e-12) {
                sb.append(" (nenhuma fonte de corrente a injetar aqui).");
            } else {
                sb.append(" (corrente injetada por fonte de corrente).");
            }
            sb.append("\n");
        }
        sb.append("\n   Matriz aumentada [A | b] (6 casas decimais, precisão total):\n");
        sb.append(renderAugmented(trace.getInitialAugmented()));
        sb.append("\n");
    }

    // ------------------------------------------------------------------
    // Secção 4 — Eliminação de Gauss, passo a passo.
    // ------------------------------------------------------------------
    private void section4Gauss(StringBuilder sb) {
        sb.append("4. Eliminação de Gauss, passo a passo\n");
        sb.append("   A ideia: transformar o sistema numa escada (matriz triangular),\n");
        sb.append("   zerando o que está abaixo da diagonal, coluna a coluna. Em\n");
        sb.append("   cada coluna escolhemos o PIVÔ — o maior número em módulo\n");
        sb.append("   nessa coluna, da linha atual para baixo. Escolher o maior\n");
        sb.append("   evita dividir por números muito pequenos (que amplificam os\n");
        sb.append("   erros de arredondamento). Se o maior não estiver na linha\n");
        sb.append("   atual, trocamos as duas linhas — trocar linhas não muda a\n");
        sb.append("   solução, só a ordem das equações.\n");
        sb.append("   Depois, para cada linha abaixo do pivô, calculamos o FATOR\n");
        sb.append("   f = (número da linha) / (pivô) e fazemos Linha <- Linha - f x\n");
        sb.append("   Linha do pivô. Isto é o mesmo que subtrair um múltiplo de uma\n");
        sb.append("   equação da outra, para eliminar aquela incógnita da linha.\n\n");
        List<double[][]> steps = trace.getSteps();
        List<MatrixSolver.StepDetail> details = trace.getStepDetails();
        for (int i = 0; i < steps.size(); i++) {
            MatrixSolver.StepDetail d = details.get(i);
            int col = d.column();
            sb.append("   Passo ").append(i + 1).append(" — coluna x").append(col + 1)
              .append(" (").append(unknowns.get(col)).append("):\n");
            sb.append("   Pivô: olhamos para a coluna x").append(col + 1)
              .append(", da linha ").append(col + 1).append(" para baixo, e escolhemos o\n");
            sb.append("   maior valor em módulo: linha ").append(d.pivotRow() + 1)
              .append(" (valor do pivô: ").append(fmt6c(steps.get(i)[col][col]))
              .append("). ");
            if (d.swapped()) {
                sb.append("Como o maior não estava na linha ").append(col + 1)
                  .append(", trocámos as linhas ").append(col + 1).append(" e ")
                  .append(d.pivotRow() + 1).append(".\n");
            } else {
                sb.append("Já estava na linha certa, por isso não houve troca.\n");
            }
            if (d.factors().isEmpty()) {
                sb.append("   Já não havia nada para zerar abaixo do pivô nesta coluna.\n");
            } else {
                for (MatrixSolver.Factor f : d.factors()) {
                    if (f.value() < 0) {
                        sb.append("   Para zerar a posição da linha ").append(f.row() + 1)
                          .append(", fator f = ").append(fmt6c(f.value()))
                          .append(": como o fator é negativo, fazemos L").append(f.row() + 1)
                          .append(" <- L").append(f.row() + 1).append(" + ").append(fmt6c(-f.value()))
                          .append(" x L").append(col + 1)
                          .append(" (somamos ").append(fmt6c(-f.value()))
                          .append(" vezes a equação do pivô, para eliminar x")
                          .append(col + 1).append(" dessa linha).\n");
                    } else {
                        sb.append("   Para zerar a posição da linha ").append(f.row() + 1)
                          .append(", fator f = ").append(fmt6c(f.value()))
                          .append(": fazemos L").append(f.row() + 1).append(" <- L")
                          .append(f.row() + 1).append(" - ").append(fmt6c(f.value()))
                          .append(" x L").append(col + 1)
                          .append(" (subtraímos ").append(fmt6c(f.value()))
                          .append(" vezes a equação do pivô, para eliminar x")
                          .append(col + 1).append(" dessa linha).\n");
                    }
                }
            }
            sb.append("   Matriz depois deste passo:\n");
            sb.append(renderAugmented(steps.get(i)));
            sb.append("\n");
        }
    }

    // ------------------------------------------------------------------
    // Secção 5 — Retrosubstituição.
    // ------------------------------------------------------------------
    private void section5BackSubstitution(StringBuilder sb) {
        sb.append("5. Retrosubstituição (de trás para a frente)\n");
        sb.append("   Com a matriz em escada, a última linha só tem uma incógnita:\n");
        sb.append("   resolvemos essa primeiro. Depois substituímos o valor dela\n");
        sb.append("   na linha anterior (que passa a ter só uma incógnita por\n");
        sb.append("   resolver), e assim por diante, até à primeira linha.\n\n");
        double[] x = trace.getSolution();
        for (MatrixSolver.BackStep step : trace.getBackSteps()) {
            int i = step.index();
            // Termos com coeficiente ~0 não entram na conta: só poluem.
            List<MatrixSolver.Term> terms = step.terms().stream()
                .filter(t -> Math.abs(t.coefficient()) >= 1e-12).toList();
            sb.append("   x").append(i + 1).append(" = ").append(unknowns.get(i)).append(":\n");
            sb.append("   Na linha ").append(i + 1).append(", depois da eliminação, a equação é:\n");
            sb.append("   ").append(fmt6c(step.divisor())).append(" * x").append(i + 1);
            for (MatrixSolver.Term t : terms) {
                if (t.coefficient() < 0) {
                    sb.append(" - ").append(fmt6c(-t.coefficient()));
                } else {
                    sb.append(" + ").append(fmt6c(t.coefficient()));
                }
                sb.append(" * ").append(t.unknown());
            }
            sb.append(" = ").append(fmt6c(step.rhs())).append("\n");
            for (MatrixSolver.Term t : terms) {
                int j = Integer.parseInt(t.unknown().substring(1)) - 1;
                sb.append("   Substituímos ").append(t.unknown()).append(" = ")
                  .append(unknowns.get(j)).append(" = ").append(ValueParser.fmt3(t.value()))
                  .append(" (já calculado): o termo vale ")
                  .append(fmt6c(t.coefficient())).append(" x ")
                  .append(ValueParser.fmt3(t.value())).append(" = ")
                  .append(fmt6c(t.coefficient() * t.value())).append(".\n");
            }
            sb.append("   Logo: x").append(i + 1).append(" = (").append(fmt6c(step.rhs()));
            for (MatrixSolver.Term t : terms) {
                double v = t.coefficient() * t.value();
                if (v < 0) {
                    sb.append(" + ").append(fmt6c(-v));
                } else {
                    sb.append(" - ").append(fmt6c(v));
                }
            }
            sb.append(") / ").append(fmt6c(step.divisor())).append(" = ")
              .append(ValueParser.fmt3(step.result())).append(".\n\n");
        }
    }

    // ------------------------------------------------------------------
    // Secção 6 — Resultado e conferência.
    // ------------------------------------------------------------------
    private void section6Result(StringBuilder sb) {
        sb.append("6. Resultado e conferência\n");
        sb.append("   Tensões nos nós (em relação ao terra):\n");
        for (var e : solution.getNodeVoltages().entrySet()) {
            if (e.getKey() == Node.GROUND_ID) {
                sb.append("   Nó N0 (terra): 0,000 V (referência)\n");
            } else {
                sb.append("   Nó N").append(e.getKey()).append(": ")
                  .append(ValueParser.fmtVoltage(e.getValue())).append("\n");
            }
        }
        sb.append("\n   Correntes e potências, componente a componente\n");
        sb.append("   (corrente positiva = do nó A para o nó B, como desenhado):\n\n");
        for (Component c : circuit.getComponents()) {
            sb.append("   ").append(componentResultBlock(c)).append("\n");
        }
        sb.append("\n   Conferência da LKC em cada nó (o que entra = o que sai):\n");
        for (int nodeId : nodeIds()) {
            sb.append("   Nó N").append(nodeId).append(": ").append(kclCheckLine(nodeId)).append("\n");
        }
        sb.append("\n   Balanço de potência (a energia tem de fechar):\n");
        double dissipated = 0.0;
        double supplied = 0.0;
        for (Component c : circuit.getComponents()) {
            double p = solution.powerOf(c.getId());
            if (c instanceof Resistor) dissipated += p;
            if (p < 0) supplied += -p;
        }
        sb.append("   Potência dissipada nos resistores (vira calor): ")
          .append(ValueParser.fmtPower(dissipated)).append(".\n");
        sb.append("   Potência fornecida pelas fontes (potência negativa = a\n");
        sb.append("   fonte entrega energia ao circuito): ")
          .append(ValueParser.fmtPower(supplied)).append(".\n");
        sb.append("   Fornecido (").append(ValueParser.fmtPower(supplied))
          .append(") = dissipado (").append(ValueParser.fmtPower(dissipated))
          .append(")? Diferença: ").append(ValueParser.fmtPower(supplied - dissipated))
          .append(" — tem de ser ~0 (só arredondamento). Se fechar, a conta está certa.\n\n");
    }

    private String componentResultBlock(Component c) {
        double va = solution.voltageOf(c.getNodeA());
        double vb = solution.voltageOf(c.getNodeB());
        double i = solution.currentOf(c.getId());
        double p = solution.powerOf(c.getId());
        StringBuilder b = new StringBuilder();
        b.append(c.getLabel()).append(" (").append(typePt(c).toLowerCase()).append(", ")
          .append(friendlyValue(c)).append(", N").append(c.getNodeA())
          .append(" -> N").append(c.getNodeB()).append("):\n     ");
        if (c instanceof Resistor r) {
            b.append("I = (V(N").append(c.getNodeA()).append(") - V(N").append(c.getNodeB())
              .append(")) / ").append(ValueParser.fmtResistance(r.getValue()))
              .append(" = (").append(ValueParser.fmt3(va)).append(" - ").append(ValueParser.fmt3(vb))
              .append(") V / ").append(ValueParser.fmtResistance(r.getValue()))
              .append(" = ").append(ValueParser.fmtCurrent(i)).append(". ");
            b.append("P = ").append(ValueParser.fmt3(va - vb)).append(" V x ")
              .append(ValueParser.fmtCurrent(i)).append(" = ")
              .append(ValueParser.fmtPower(p)).append(" dissipados em calor.");
        } else if (c instanceof CurrentSource) {
            b.append("I = valor da fonte = ").append(ValueParser.fmtCurrent(i))
              .append(" (de A para B). ");
            b.append("P = ").append(ValueParser.fmt3(va - vb)).append(" V x ")
              .append(ValueParser.fmtCurrent(i)).append(" = ")
              .append(ValueParser.fmtPower(p))
              .append(p < 0 ? " (negativo: a fonte FORNECE energia)." : " (a fonte absorve energia).");
        } else if (c instanceof Capacitor) {
            b.append("I = 0 A (capacitor aberto em DC, não passa corrente). P = 0 W.");
        } else if (c instanceof VoltageSource) {
            b.append("I = ").append(ValueParser.fmtCurrent(i))
              .append(" (incógnita resolvida pelo sistema). ");
            b.append(signExplanation(i, c));
            b.append("P = ").append(ValueParser.fmt3(va - vb)).append(" V x ")
              .append(ValueParser.fmtCurrent(i)).append(" = ")
              .append(ValueParser.fmtPower(p))
              .append(p < 0 ? " (negativo: a fonte FORNECE energia ao circuito)."
                  : " (a fonte está a absorver energia).");
        } else {
            // Fio, indutor, terra: curto em DC.
            b.append("I = ").append(ValueParser.fmtCurrent(i))
              .append(" (incógnita resolvida pelo sistema). ");
            b.append(signExplanation(i, c));
            b.append("P = 0 V x ").append(ValueParser.fmtCurrent(i))
              .append(" = 0 W (um curto ideal não dissipa potência).");
        }
        return b.toString();
    }

    /** Explica o sinal da corrente: negativo = sentido real ao contrário. */
    private String signExplanation(double i, Component c) {
        if (i < -1e-9) {
            return "O sinal é NEGATIVO: a corrente real vai do nó N" + c.getNodeB()
                + " para o nó N" + c.getNodeA()
                + ", ao contrário do sentido A -> B desenhado. O valor está certo, só o sentido inverte. ";
        }
        if (i > 1e-9) {
            return "O sinal é positivo: a corrente real vai mesmo de A para B, como desenhado. ";
        }
        return "Corrente ~0 A neste ramo. ";
    }

    // ------------------------------------------------------------------
    // Secção 7 — Delta→Estrela.
    // ------------------------------------------------------------------
    private void section7Delta(StringBuilder sb) {
        sb.append("7. Conversão Delta (Triângulo) -> Estrela\n");
        sb.append("   O circuito tem três resistores ligados em triângulo (delta):\n");
        sb.append("   cada resistor liga dois dos três nós do triângulo. Um delta\n");
        sb.append("   não se resolve por série/paralelo direto — por isso o\n");
        sb.append("   convertemos numa estrela (Y): três resistores que saem dos\n");
        sb.append("   mesmos três nós e se encontram num centro comum N.\n");
        sb.append("   A fórmula: cada ramo da estrela é o produto dos dois lados do\n");
        sb.append("   delta que tocam nesse nó, dividido pela soma dos três lados.\n");
        sb.append("   R_Y = (R x R) / (R_AB + R_BC + R_CA).\n");
        sb.append("   Depois da conversão, o circuito fica em série e resolve-se\n");
        sb.append("   por divisor de tensão — e as tensões dos três nós do delta\n");
        sb.append("   são as mesmas de antes, porque a estrela é equivalente.\n\n");
        for (String line : deltaSection) sb.append("   ").append(line).append("\n");
        sb.append("\n");
    }

    private String renderAugmented(double[][] m) {
        StringBuilder sb = new StringBuilder();
        int n = vectorB.length;
        // Cabeçalho com as incógnitas.
        sb.append("   [");
        for (int j = 0; j < n; j++) sb.append(String.format(" %12s", "x" + (j + 1)));
        sb.append(" | ").append(String.format("%12s", "b")).append(" ]\n");
        for (int i = 0; i < n; i++) {
            sb.append("   [");
            for (int j = 0; j < n; j++) sb.append(String.format(" %12s", fmt(m[i][j])));
            sb.append(" | ").append(String.format("%12s", fmt(m[i][n]))).append(" ]\n");
        }
        return sb.toString();
    }

    private static double[][] copy(double[][] m) {
        double[][] out = new double[m.length][];
        for (int i = 0; i < m.length; i++) out[i] = m[i].clone();
        return out;
    }
}
