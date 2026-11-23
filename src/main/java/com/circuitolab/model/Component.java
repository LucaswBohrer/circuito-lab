package com.circuitolab.model;

/**
 * Componente de dois terminais. A corrente positiva flui do nó A para o nó B.
 * O valor tem unidade conforme o tipo: ohms (resistor), volts (fonte de
 * tensão), amps (fonte de corrente), farads (capacitor), henries (indutor).
 */
public abstract class Component {
    private final String id;
    private String label;
    private int nodeA;
    private int nodeB;
    private double value;
    private int x1, y1, x2, y2;

    protected Component(String id, String label, int nodeA, int nodeB, double value,
                        int x1, int y1, int x2, int y2) {
        this.id = id;
        this.label = label;
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.value = value;
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    public abstract String getType();

    /** Unidade do valor (ex.: "Ω", "V", "A"). */
    public abstract String getUnit();

    /** True se o componente participa da análise DC como ramo de tensão (fonte 0 V ou com valor). */
    public boolean isVoltageBranch() { return false; }

    /** True se o componente é um circuito aberto em DC (capacitor). */
    public boolean isOpenInDc() { return false; }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getNodeA() { return nodeA; }
    public int getNodeB() { return nodeB; }
    public void setNodeA(int nodeA) { this.nodeA = nodeA; }
    public void setNodeB(int nodeB) { this.nodeB = nodeB; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public void setPosition(int x1, int y1, int x2, int y2) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    /** Ponto médio, usado para desenhar o rótulo na GUI. */
    public int getMidX() { return (x1 + x2) / 2; }
    public int getMidY() { return (y1 + y2) / 2; }

    @Override
    public String toString() { return label + " (" + getType() + ")"; }
}
