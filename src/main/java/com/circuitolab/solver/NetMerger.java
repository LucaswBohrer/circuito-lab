package com.circuitolab.solver;

import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.model.Ground;
import com.circuitolab.model.Inductor;
import com.circuitolab.model.Node;
import com.circuitolab.model.Wire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta os nós que são o mesmo ponto elétrico — as "redes" (nets).
 *
 * Um fio, um indutor ou o terra, em DC, são um curto (0 V): os dois
 * nós que eles ligam têm a mesma tensão, por isso são o mesmo ponto.
 * Uma fonte de tensão NÃO junta nós: ela fixa uma diferença de tensão
 * entre eles. Resistores, capacitores e fontes de corrente também não.
 *
 * Isto é só para a apresentação/explicação (o passo a passo simples):
 * o solver MNA interno e o modelo do circuito não mudam — nenhum fio
 * é apagado, nenhum nó é renumerado.
 *
 * O representante de uma rede é o menor id dos seus nós; o terra (id 0)
 * vence sempre que está na rede.
 */
public final class NetMerger {

    /** Uma rede: os nós que são o mesmo ponto, e os seus componentes. */
    public record Net(int representative, List<Integer> nodes, List<Component> components) {
        /** True se esta rede é o terra (contém o nó 0). */
        public boolean isGround() { return nodes.contains(Node.GROUND_ID); }
    }

    private final Circuit circuit;
    private final Map<Integer, Integer> netOf = new LinkedHashMap<>();
    private final List<Net> nets = new ArrayList<>();

    public NetMerger(Circuit circuit) {
        this.circuit = circuit;
        merge();
    }

    /** Atalho: as redes de um circuito. */
    public static List<Net> merge(Circuit circuit) {
        return new NetMerger(circuit).nets();
    }

    /** Todas as redes, por ordem do representante (o terra primeiro). */
    public List<Net> nets() { return Collections.unmodifiableList(nets); }

    /** O representante (menor id) da rede a que pertence o nó. */
    public int netOf(int nodeId) {
        return netOf.getOrDefault(nodeId, nodeId);
    }

    /** A rede a que pertence o nó. */
    public Net netContaining(int nodeId) {
        int rep = netOf(nodeId);
        for (Net net : nets) {
            if (net.representative() == rep) return net;
        }
        return null;
    }

    private void merge() {
        // Union-find sobre os ids dos nós.
        Map<Integer, Integer> parent = new LinkedHashMap<>();
        for (Node n : circuit.getNodes()) parent.put(n.getId(), n.getId());
        for (Component c : circuit.getComponents()) {
            if (isShortInDc(c)) union(parent, c.getNodeA(), c.getNodeB());
        }
        // Agrupa os nós por raiz; o representante é o menor id do grupo.
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (Node n : circuit.getNodes()) {
            int root = find(parent, n.getId());
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(n.getId());
        }
        List<List<Integer>> ordered = new ArrayList<>(groups.values());
        for (List<Integer> g : ordered) g.sort(Integer::compareTo);
        ordered.sort((a, b) -> Integer.compare(a.get(0), b.get(0)));
        for (List<Integer> g : ordered) {
            int rep = g.get(0);
            for (int id : g) netOf.put(id, rep);
            List<Component> touching = new ArrayList<>();
            for (Component c : circuit.getComponents()) {
                if (g.contains(c.getNodeA()) || g.contains(c.getNodeB())) touching.add(c);
            }
            nets.add(new Net(rep, List.copyOf(g), List.copyOf(touching)));
        }
    }

    /** Fio, indutor e terra são um curto em DC (0 V): os nós são o mesmo ponto. */
    public static boolean isShortInDc(Component c) {
        return c instanceof Wire || c instanceof Inductor || c instanceof Ground;
    }

    private static int find(Map<Integer, Integer> parent, int x) {
        int p = parent.getOrDefault(x, x);
        if (p == x) return x;
        int root = find(parent, p);
        parent.put(x, root);
        return root;
    }

    private static void union(Map<Integer, Integer> parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return;
        // A raiz passa a ser o menor id: o terra (0) vence sempre.
        int root = Math.min(ra, rb);
        int other = root == ra ? rb : ra;
        parent.put(other, root);
    }
}
