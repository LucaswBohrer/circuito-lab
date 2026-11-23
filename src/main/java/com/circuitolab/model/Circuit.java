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

    /** Próximo id livre para um tipo, ex.: resistor -> "R3". */
    public String nextId(String prefix) {
        return prefix + (nextComponentSeq++);
    }

    /** True se algum componente liga um nó ao terra (0). */
    public boolean hasExplicitGround() {
        for (Component c : components) {
            if (c.getNodeA() == Node.GROUND_ID || c.getNodeB() == Node.GROUND_ID) return true;
        }
        return false;
    }
}
