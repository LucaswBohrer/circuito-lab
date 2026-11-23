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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Análise DC por Análise Nodal Modificada (MNA).
 *
 * Convenções:
 * - Nó 0 = terra (0 V). Fonte de tensão: V(A) - V(B) = valor.
 * - Corrente positiva de um componente flui do nó A para o nó B.
 * - Capacitor em DC = circuito aberto; indutor e fio em DC = curto
 *   (ramo de tensão de 0 V).
 *
 * solveWithSteps() devolve o mesmo resultado de solve() mais o passo a
 * passo completo (equações, matriz, eliminação de Gauss, Delta→Estrela).
 */
public final class MnaSolver {
    private MnaSolver() {}

    /** Sistema MNA montado: incógnitas, matriz A e vetor b. */
    private record BuiltSystem(List<Integer> nodeIds, Map<Integer, Integer> nodeIndex,
                               List<Component> branches, double[][] a, double[] b) {}

    public static Solution solve(Circuit circuit) throws SolverException {
        BuiltSystem sys = buildSystem(circuit);
        double[] x = MatrixSolver.solve(sys.a(), sys.b());
        return buildSolution(circuit, sys, x);
    }

    /** Resolve e devolve o passo a passo completo da resolução. */
    public static StepByStep solveWithSteps(Circuit circuit) throws SolverException {
        BuiltSystem sys = buildSystem(circuit);
        MatrixSolver.Trace trace = MatrixSolver.solveWithTrace(sys.a(), sys.b());
        Solution solution = buildSolution(circuit, sys, trace.getSolution());

        List<String> unknowns = new ArrayList<>();
        for (int id : sys.nodeIds()) unknowns.add("V(N" + id + ")");
        for (Component c : sys.branches()) unknowns.add("I(" + c.getLabel() + ")");

        return new StepByStep(circuit, unknowns, sys.a(), sys.b(),
            buildEquations(circuit, sys), trace, solution,
            buildDeltaSection(circuit, solution));
    }

    private static BuiltSystem buildSystem(Circuit circuit) throws SolverException {
        if (circuit == null) throw new SolverException("Circuito nulo.");
        if (circuit.getComponents().isEmpty()) {
            throw new SolverException("Circuito vazio: adicione componentes antes de resolver.");
        }
        if (!circuit.hasExplicitGround()) {
            throw new SolverException(
                "Circuito sem terra: adicione um componente Terra ligado a um nó "
                + "(o terra é o nó de referência, 0 V).");
        }

        validateComponents(circuit);
        checkFloatingNodes(circuit);

        // Índices MNA para os nós (exceto o terra).
        List<Integer> nodeIds = new ArrayList<>();
        Map<Integer, Integer> nodeIndex = new HashMap<>();
        for (Component c : circuit.getComponents()) {
            for (int id : new int[]{c.getNodeA(), c.getNodeB()}) {
                if (id != Node.GROUND_ID && !nodeIndex.containsKey(id)) {
                    nodeIndex.put(id, nodeIds.size());
                    nodeIds.add(id);
                }
            }
        }

        // Ramos de tensão: fonte de tensão, indutor, fio e terra.
        List<Component> branches = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c.isVoltageBranch()) branches.add(c);
        }

        int n = nodeIds.size();
        int m = branches.size();
        int size = n + m;
        double[][] a = new double[size][size];
        double[] z = new double[size];

        for (Component c : circuit.getComponents()) {
            int ia = c.getNodeA() == Node.GROUND_ID ? -1 : nodeIndex.get(c.getNodeA());
            int ib = c.getNodeB() == Node.GROUND_ID ? -1 : nodeIndex.get(c.getNodeB());

            if (c instanceof Resistor r) {
                if (r.getValue() <= 0) {
                    throw new SolverException("Resistor " + r.getLabel()
                        + " com resistência inválida (deve ser > 0 Ω).");
                }
                double g = 1.0 / r.getValue();
                if (ia >= 0) a[ia][ia] += g;
                if (ib >= 0) a[ib][ib] += g;
                if (ia >= 0 && ib >= 0) { a[ia][ib] -= g; a[ib][ia] -= g; }
            } else if (c instanceof CurrentSource cs) {
                if (ia >= 0) z[ia] -= cs.getValue();
                if (ib >= 0) z[ib] += cs.getValue();
            } else if (c instanceof Capacitor) {
                // Circuito aberto em DC: não contribui.
            }
            // Ramos de tensão são carimbados abaixo, com o seu índice de ramo.
        }

        for (int k = 0; k < branches.size(); k++) {
            Component c = branches.get(k);
            int row = n + k;
            int ia = c.getNodeA() == Node.GROUND_ID ? -1 : nodeIndex.get(c.getNodeA());
            int ib = c.getNodeB() == Node.GROUND_ID ? -1 : nodeIndex.get(c.getNodeB());
            double v = (c instanceof VoltageSource) ? c.getValue() : 0.0;
            if (ia >= 0) { a[row][ia] = 1.0; a[ia][row] = 1.0; }
            if (ib >= 0) { a[row][ib] = -1.0; a[ib][row] = -1.0; }
            z[row] = v;
        }
        return new BuiltSystem(nodeIds, nodeIndex, branches, a, z);
    }

    private static Solution buildSolution(Circuit circuit, BuiltSystem sys, double[] x) {
        int n = sys.nodeIds().size();
        Map<Integer, Double> voltages = new LinkedHashMap<>();
        voltages.put(Node.GROUND_ID, 0.0);
        for (int i = 0; i < sys.nodeIds().size(); i++) {
            voltages.put(sys.nodeIds().get(i), x[i]);
        }

        Map<String, Double> currents = new LinkedHashMap<>();
        Map<String, Double> powers = new LinkedHashMap<>();
        Map<Component, Integer> branchIndex = new HashMap<>();
        for (int k = 0; k < sys.branches().size(); k++) branchIndex.put(sys.branches().get(k), k);

        for (Component c : circuit.getComponents()) {
            double va = voltages.getOrDefault(c.getNodeA(), 0.0);
            double vb = voltages.getOrDefault(c.getNodeB(), 0.0);
            double current;
            if (c instanceof Resistor r) {
                current = (va - vb) / r.getValue();
            } else if (c instanceof CurrentSource) {
                current = c.getValue();
            } else if (c instanceof Capacitor) {
                current = 0.0;
            } else if (branchIndex.containsKey(c)) {
                current = x[n + branchIndex.get(c)];
            } else {
                current = 0.0;
            }
            currents.put(c.getId(), current);
            powers.put(c.getId(), (va - vb) * current);
        }
        return new Solution(voltages, currents, powers);
    }

    /**
     * Equações do sistema, em texto: uma equação LKC por nó (soma das
     * correntes que saem do nó = 0) e uma equação por ramo de tensão.
     */
    private static List<String> buildEquations(Circuit circuit, BuiltSystem sys) {
        List<String> eqs = new ArrayList<>();
        for (int nodeId : sys.nodeIds()) {
            List<String> terms = new ArrayList<>();
            for (Component c : circuit.getComponents()) {
                if (c instanceof Resistor r) {
                    if (c.getNodeA() == nodeId) {
                        terms.add("+ (" + StepByStep.vLabel(nodeId) + " - "
                            + StepByStep.vLabel(c.getNodeB()) + ") / " + fmtEq(r.getValue()));
                    } else if (c.getNodeB() == nodeId) {
                        terms.add("+ (" + StepByStep.vLabel(nodeId) + " - "
                            + StepByStep.vLabel(c.getNodeA()) + ") / " + fmtEq(r.getValue()));
                    }
                } else if (c instanceof CurrentSource cs) {
                    if (c.getNodeA() == nodeId) terms.add("+ " + fmtEq(cs.getValue()) + " A");
                    else if (c.getNodeB() == nodeId) terms.add("- " + fmtEq(cs.getValue()) + " A");
                } else if (c.isVoltageBranch()) {
                    if (c.getNodeA() == nodeId) terms.add("+ I(" + c.getLabel() + ")");
                    else if (c.getNodeB() == nodeId) terms.add("- I(" + c.getLabel() + ")");
                }
                // Capacitor em DC: aberto, não entra na equação.
            }
            StringBuilder eq = new StringBuilder("Nó " + nodeId + ": ");
            if (terms.isEmpty()) {
                eq.append("0 = 0");
            } else {
                String first = terms.get(0);
                eq.append(first.startsWith("+ ") ? first.substring(2) : first);
                for (int i = 1; i < terms.size(); i++) eq.append(" ").append(terms.get(i));
                eq.append(" = 0");
            }
            eqs.add(eq.toString());
        }
        for (Component c : sys.branches()) {
            double v = (c instanceof VoltageSource) ? c.getValue() : 0.0;
            eqs.add(c.getLabel() + ": " + StepByStep.vLabel(c.getNodeA()) + " - "
                + StepByStep.vLabel(c.getNodeB()) + " = " + fmtEq(v) + " V");
        }
        return eqs;
    }

    /**
     * Secção Delta→Estrela: procura o primeiro triângulo de resistores
     * (3 resistores entre 3 nós distintos) e calcula a estrela equivalente
     * R_Y = R·R / (soma dos 3 R do delta), com a tensão do centro.
     */
    private static List<String> buildDeltaSection(Circuit circuit, Solution solution) {
        List<Resistor> resistors = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c instanceof Resistor r) resistors.add(r);
        }
        // Mapa par de nós (ordenado) -> resistor do delta.
        Map<String, Resistor> byPair = new HashMap<>();
        for (Resistor r : resistors) {
            byPair.put(pairKey(r.getNodeA(), r.getNodeB()), r);
        }
        Set<Integer> nodes = new HashSet<>();
        for (Resistor r : resistors) {
            nodes.add(r.getNodeA());
            nodes.add(r.getNodeB());
        }
        List<Integer> sorted = new ArrayList<>(nodes);
        sorted.sort(Integer::compareTo);
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                for (int k = j + 1; k < sorted.size(); k++) {
                    int a = sorted.get(i), b = sorted.get(j), cc = sorted.get(k);
                    Resistor rab = byPair.get(pairKey(a, b));
                    Resistor rbc = byPair.get(pairKey(b, cc));
                    Resistor rca = byPair.get(pairKey(cc, a));
                    if (rab == null || rbc == null || rca == null) continue;
                    return deltaLines(a, b, cc, rab, rbc, rca, solution);
                }
            }
        }
        return List.of();
    }

    private static List<String> deltaLines(int a, int b, int c,
                                           Resistor rab, Resistor rbc, Resistor rca,
                                           Solution solution) {
        double sum = rab.getValue() + rbc.getValue() + rca.getValue();
        double rya = rab.getValue() * rca.getValue() / sum;
        double ryb = rab.getValue() * rbc.getValue() / sum;
        double ryc = rbc.getValue() * rca.getValue() / sum;
        double va = solution.voltageOf(a);
        double vb = solution.voltageOf(b);
        double vc = solution.voltageOf(c);
        // Tensão do centro da estrela (LKC no centro: soma (VN - Vi)/RYi = 0).
        double ga = 1.0 / rya, gb = 1.0 / ryb, gc = 1.0 / ryc;
        double vn = (va * ga + vb * gb + vc * gc) / (ga + gb + gc);
        List<String> lines = new ArrayList<>();
        lines.add("Delta encontrado entre os nós N" + a + ", N" + b + " e N" + c + ": "
            + rab.getLabel() + " = " + fmtEq(rab.getValue()) + " Ω (N" + a + "-N" + b + "), "
            + rbc.getLabel() + " = " + fmtEq(rbc.getValue()) + " Ω (N" + b + "-N" + c + "), "
            + rca.getLabel() + " = " + fmtEq(rca.getValue()) + " Ω (N" + c + "-N" + a + ").");
        lines.add("Fórmulas: R_Y = (R_delta x R_delta) / (R_AB + R_BC + R_CA).");
        lines.add(String.format(Locale.US, "R_Y(N%d) = %.6f x %.6f / %.6f = %s Ω (entre N%d e o centro N).",
            a, rab.getValue(), rca.getValue(), sum, StepByStep.fmt(rya), a));
        lines.add(String.format(Locale.US, "R_Y(N%d) = %.6f x %.6f / %.6f = %s Ω (entre N%d e o centro N).",
            b, rab.getValue(), rbc.getValue(), sum, StepByStep.fmt(ryb), b));
        lines.add(String.format(Locale.US, "R_Y(N%d) = %.6f x %.6f / %.6f = %s Ω (entre N%d e o centro N).",
            c, rbc.getValue(), rca.getValue(), sum, StepByStep.fmt(ryc), c));
        lines.add("Tensão do centro da estrela (LKC no centro): V(N) = "
            + StepByStep.fmt(vn) + " V.");
        lines.add("Corrente em cada ramo da estrela: I = (V(nó) - V(N)) / R_Y — "
            + "num ramo sem carga a corrente é zero e a tensão do nó é a do centro.");
        lines.add("Com o delta substituído pela estrela, o circuito fica em série "
            + "e resolve-se por divisor de tensão.");
        return lines;
    }

    private static String pairKey(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    private static String fmtEq(double v) {
        return StepByStep.fmt(v);
    }

    private static void validateComponents(Circuit circuit) throws SolverException {
        for (Component c : circuit.getComponents()) {
            if (c instanceof VoltageSource) {
                if (c.getNodeA() == c.getNodeB()) {
                    throw new SolverException("Fonte de tensão " + c.getLabel()
                        + " em curto-circuito: os dois terminais estão no mesmo nó.");
                }
            }
            if (c instanceof Wire || c instanceof Inductor || c instanceof Ground) {
                if (c.getNodeA() == c.getNodeB() && !(c instanceof Ground)) {
                    throw new SolverException("Componente " + c.getLabel()
                        + " em curto-circuito: os dois terminais estão no mesmo nó.");
                }
            }
            if (circuit.getNode(c.getNodeA()) == null || circuit.getNode(c.getNodeB()) == null) {
                throw new SolverException("Componente " + c.getLabel()
                    + " refere um nó inexistente.");
            }
        }
        // Fontes de tensão em paralelo com valores diferentes (ou malha de
        // fontes) tornam o sistema singular; o MatrixSolver deteta o caso
        // geral. Aqui detetamos o caso paralelo explícito para dar erro claro.
        List<VoltageSource> sources = new ArrayList<>();
        for (Component c : circuit.getComponents()) {
            if (c instanceof VoltageSource vs) sources.add(vs);
        }
        for (int i = 0; i < sources.size(); i++) {
            for (int j = i + 1; j < sources.size(); j++) {
                VoltageSource s1 = sources.get(i);
                VoltageSource s2 = sources.get(j);
                boolean sameNodes = s1.getNodeA() == s2.getNodeA() && s1.getNodeB() == s2.getNodeB();
                boolean swapped = s1.getNodeA() == s2.getNodeB() && s1.getNodeB() == s2.getNodeA();
                if (sameNodes && Math.abs(s1.getValue() - s2.getValue()) > 1e-9) {
                    throw new SolverException("Fontes de tensão " + s1.getLabel() + " e "
                        + s2.getLabel() + " em paralelo com valores diferentes: "
                        + "malha de fontes de tensão inconsistente.");
                }
                if (swapped && Math.abs(s1.getValue() + s2.getValue()) > 1e-9) {
                    throw new SolverException("Fontes de tensão " + s1.getLabel() + " e "
                        + s2.getLabel() + " em paralelo com valores diferentes: "
                        + "malha de fontes de tensão inconsistente.");
                }
            }
        }
    }

    /**
     * Nó flutuante: nó usado no circuito que não tem caminho condutivo
     * (resistor, fonte de tensão, indutor, fio ou terra) até ao terra.
     * Capacitor e fonte de corrente não contam como caminho condutivo em DC.
     */
    private static void checkFloatingNodes(Circuit circuit) throws SolverException {
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (Component c : circuit.getComponents()) {
            used.add(c.getNodeA());
            used.add(c.getNodeB());
            if (c instanceof Capacitor || c instanceof CurrentSource) continue;
            adj.computeIfAbsent(c.getNodeA(), k -> new HashSet<>()).add(c.getNodeB());
            adj.computeIfAbsent(c.getNodeB(), k -> new HashSet<>()).add(c.getNodeA());
        }
        Set<Integer> reached = new HashSet<>();
        Queue<Integer> queue = new ArrayDeque<>();
        reached.add(Node.GROUND_ID);
        queue.add(Node.GROUND_ID);
        while (!queue.isEmpty()) {
            int cur = queue.remove();
            for (int next : adj.getOrDefault(cur, Set.of())) {
                if (reached.add(next)) queue.add(next);
            }
        }
        for (int id : used) {
            if (id != Node.GROUND_ID && !reached.contains(id)) {
                throw new SolverException("Nó " + id + " flutuante: não tem caminho "
                    + "até ao terra (verifique se está ligado por resistor, fio ou fonte).");
            }
        }
    }
}
