package com.circuitolab.ui;

import com.circuitolab.model.Circuit;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;
import com.circuitolab.model.VoltageSource;

/** Circuitos de exemplo embutidos no menu (coordenadas em múltiplos de 20 px). */
public final class Examples {
    private Examples() {}

    public static Circuit divisorTensao() {
        Circuit c = new Circuit("Divisor de Tensão");
        Node n1 = c.addNode(200, 100);
        Node n2 = c.addNode(400, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 200, 100, 400, 100));
        c.addComponent(new Resistor("R2", "R2", n2.getId(), Node.GROUND_ID, 2000, 400, 100, 400, 300));
        return c;
    }

    public static Circuit ponteWheatstone() {
        Circuit c = new Circuit("Ponte de Wheatstone");
        Node n1 = c.addNode(200, 100);
        Node n2 = c.addNode(400, 100);
        Node n3 = c.addNode(600, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 200, 100, 400, 100));
        c.addComponent(new Resistor("R2", "R2", n1.getId(), n3.getId(), 1000, 200, 100, 600, 200));
        c.addComponent(new Resistor("R3", "R3", n2.getId(), Node.GROUND_ID, 1000, 400, 100, 400, 300));
        c.addComponent(new Resistor("R4", "R4", n3.getId(), Node.GROUND_ID, 1000, 600, 200, 600, 400));
        c.addComponent(new Resistor("R5", "R5", n2.getId(), n3.getId(), 1000, 400, 100, 600, 200));
        return c;
    }

    /** Resistores em série: 12 V, 1k + 2k + 3k => I = 2 mA; quedas 2 V, 4 V, 6 V. */
    public static Circuit serie() {
        Circuit c = new Circuit("Resistores em Série");
        Node n1 = c.addNode(200, 100);
        Node n2 = c.addNode(400, 100);
        Node n3 = c.addNode(600, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 200, 100, 400, 100));
        c.addComponent(new Resistor("R2", "R2", n2.getId(), n3.getId(), 2000, 400, 100, 600, 100));
        c.addComponent(new Resistor("R3", "R3", n3.getId(), Node.GROUND_ID, 3000, 600, 100, 600, 300));
        return c;
    }

    /**
     * Delta-Estrela (Triângulo-Estrela): fonte 12 V, resistor série de 10 Ω
     * até ao nó A; delta de 30 Ω entre A (cima), B (direita) e C (baixo);
     * carga de 15 Ω do nó C ao terra.
     * Conversão: R_Y = 30/3 = 10 Ω por ramo. Total em série: 10 + 10 + 10 + 15
     * = 45 Ω => I total = 12/45 = 0,2667 A; VA = 9,333 V; VB = 6,667 V
     * (ramo B sem corrente: VB é a tensão do centro da estrela); VC = 4,000 V.
     */
    public static Circuit deltaEstrela() {
        Circuit c = new Circuit("Delta-Estrela (Triângulo-Estrela)");
        Node n1 = c.addNode(200, 100); // topo da fonte
        Node nA = c.addNode(400, 100); // A: cima do triângulo
        Node nB = c.addNode(600, 200); // B: direita do triângulo
        Node nC = c.addNode(400, 300); // C: baixo do triângulo
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), nA.getId(), 10, 200, 100, 400, 100));
        c.addComponent(new Resistor("R2", "R2", nA.getId(), nB.getId(), 30, 400, 100, 600, 200));
        c.addComponent(new Resistor("R3", "R3", nB.getId(), nC.getId(), 30, 600, 200, 400, 300));
        c.addComponent(new Resistor("R4", "R4", nA.getId(), nC.getId(), 30, 400, 100, 400, 300));
        c.addComponent(new Resistor("R5", "R5", nC.getId(), Node.GROUND_ID, 15, 400, 300, 400, 420));
        return c;
    }

    public static Circuit paralelo() {
        Circuit c = new Circuit("Resistores em Paralelo");
        Node n1 = c.addNode(200, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), Node.GROUND_ID, 1000, 200, 100, 400, 300));
        c.addComponent(new Resistor("R2", "R2", n1.getId(), Node.GROUND_ID, 2000, 200, 100, 500, 300));
        return c;
    }
}
