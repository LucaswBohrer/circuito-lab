package com.circuitolab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um circuito: nós + componentes. O nó 0 (terra) sempre existe. */
public class Circuit {
    private String name;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Component> components = new ArrayList<>();
    private int nextNodeId = 1;
    private int nextComponentSeq = 1;

    public Circuit(String name) {
        this.name = name;
        nodes.add(new Node(Node.GROUND_ID, 0, 0));
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Node> getNodes() { return Collections.unmodifiableList(nodes); }
    public List<Component> getComponents() { return Collections.unmodifiableList(components); }

    public Node getNode(int id) {
        for (Node n : nodes) {
            if (n.getId() == id) return n;
        }
        return null;
    }

    /** Cria um nó novo e o devolve. */
    public Node addNode(int x, int y) {
        Node n = new Node(nextNodeId++, x, y);
        nodes.add(n);
        return n;
    }

    /** Regista um nó carregado de ficheiro (preserva o id). */
    public void addNodeWithId(Node node) {
        if (getNode(node.getId()) != null) return;
        nodes.add(node);
        if (node.getId() >= nextNodeId) nextNodeId = node.getId() + 1;
    }

    public void addComponent(Component c) {
        components.add(c);
        // Mantém o contador de ids sincronizado com ids carregados (ex.: "R12").
        String digits = c.getId().replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            try {
                int n = Integer.parseInt(digits);
                if (n >= nextComponentSeq) nextComponentSeq = n + 1;
            } catch (NumberFormatException ignored) {
                // id sem número válido: ignora
            }
        }
    }

    public boolean removeComponent(Component c) { return components.remove(c); }

    /** Remove o nó se nenhum componente o usar (nunca remove o terra). */
    public boolean removeNodeIfUnused(int id) {
        if (id == Node.GROUND_ID) return false;
        for (Component c : components) {
            if (c.getNodeA() == id || c.getNodeB() == id) return false;
        }
        return nodes.removeIf(n -> n.getId() == id);
    }

    /**
     * Remove todos os nós soltos (sem nenhum componente ligado) — ex.:
     * nós deixados por cliques antigos. Nunca remove o terra.
     * Devolve quantos nós foram removidos.
     */
    public int pruneUnusedNodes() {
        int removed = 0;
        List<Node> copy = new ArrayList<>(nodes);
        for (Node n : copy) {
            if (removeNodeIfUnused(n.getId())) removed++;
        }
        return removed;
    }

    /** Próximo id livre para um tipo, ex.: resistor -> "R3". */
    public String nextId(String prefix) {
        return prefix + (nextComponentSeq++);
    }

    /** True se já existe outro componente com este rótulo (ignora {@code exclude}). */
    public boolean isLabelUsed(String label, Component exclude) {
        if (label == null) return false;
        String wanted = label.trim();
        for (Component c : components) {
            if (c != exclude && c.getLabel().equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    /**
     * Renumera os rótulos em ordem de criação, por tipo (R1, R2, R3...;
     * V1, V2...; I1...; C1...; L1...; W1...). Só o rótulo muda — o id
     * interno não muda, por isso a Solution (indexada por id) continua
     * válida. O terra (GND) não é renumerado.
     * Devolve quantos rótulos mudaram.
     */
    public int renumberComponents() {
        java.util.Map<String, Integer> counters = new java.util.LinkedHashMap<>();
        int changed = 0;
        int maxNumber = 0;
        for (Component c : components) {
            String prefix = prefixOf(c);
            if (prefix == null) continue; // terra: não renumera
            int n = counters.getOrDefault(prefix, 0) + 1;
            counters.put(prefix, n);
            String newLabel = prefix + n;
            if (!newLabel.equals(c.getLabel())) {
                c.setLabel(newLabel);
                changed++;
            }
            if (n > maxNumber) maxNumber = n;
        }
        // O próximo componente criado não pode reutilizar um rótulo
        // que já existe: o contador global passa para depois do maior.
        if (maxNumber >= nextComponentSeq) nextComponentSeq = maxNumber + 1;
        return changed;
    }

    /**
     * Renumera os nós com ids sequenciais (o terra continua 0, os demais
     * em ordem de criação) e atualiza nodeA/nodeB de cada componente.
     * Node.getId() é final, por isso os objetos Node são recriados.
     * As tensões e correntes da solução não mudam — só os números dos
     * nós. Devolve o mapa id antigo -> id novo.
     */
    public java.util.Map<Integer, Integer> renumberNodes() {
        java.util.Map<Integer, Integer> mapping = new java.util.LinkedHashMap<>();
        mapping.put(Node.GROUND_ID, Node.GROUND_ID);
        List<Node> newNodes = new ArrayList<>();
        int next = 1;
        for (Node n : nodes) {
            if (n.isGround()) {
                newNodes.add(n);
                continue;
            }
            Node renamed = new Node(next, n.getX(), n.getY());
            mapping.put(n.getId(), next);
            newNodes.add(renamed);
            next++;
        }
        nodes.clear();
        nodes.addAll(newNodes);
        nextNodeId = next;
        for (Component c : components) {
            c.setNodeA(mapping.getOrDefault(c.getNodeA(), c.getNodeA()));
            c.setNodeB(mapping.getOrDefault(c.getNodeB(), c.getNodeB()));
        }
        return mapping;
    }

    /** Renumera componentes e nós (o mesmo menu faz os dois). */
    public void renumberAll() {
        renumberComponents();
        renumberNodes();
    }

    /** Prefixo do rótulo por tipo de componente; null = não renumerar (terra). */
    private static String prefixOf(Component c) {
        if (c instanceof com.circuitolab.model.Resistor) return "R";
        if (c instanceof com.circuitolab.model.VoltageSource) return "V";
        if (c instanceof com.circuitolab.model.CurrentSource) return "I";
        if (c instanceof com.circuitolab.model.Capacitor) return "C";
        if (c instanceof com.circuitolab.model.Inductor) return "L";
        if (c instanceof com.circuitolab.model.Wire) return "W";
        return null;
    }

    /** True se algum componente liga um nó ao terra (0). */
    public boolean hasExplicitGround() {
        for (Component c : components) {
            if (c.getNodeA() == Node.GROUND_ID || c.getNodeB() == Node.GROUND_ID) return true;
        }
        return false;
    }
}
