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
 */
public final class MnaSolver {
    private MnaSolver() {}

    public static Solution solve(Circuit circuit) throws SolverException {
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

        double[] x = MatrixSolver.solve(a, z);

        Map<Integer, Double> voltages = new LinkedHashMap<>();
        voltages.put(Node.GROUND_ID, 0.0);
        for (int i = 0; i < nodeIds.size(); i++) {
            voltages.put(nodeIds.get(i), x[i]);
        }

        Map<String, Double> currents = new LinkedHashMap<>();
        Map<String, Double> powers = new LinkedHashMap<>();
        Map<Component, Integer> branchIndex = new HashMap<>();
        for (int k = 0; k < branches.size(); k++) branchIndex.put(branches.get(k), k);

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
