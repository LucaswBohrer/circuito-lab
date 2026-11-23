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

    public static Circuit paralelo() {
        Circuit c = new Circuit("Resistores em Paralelo");
        Node n1 = c.addNode(200, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 200, 100, 200, 300));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), Node.GROUND_ID, 1000, 200, 100, 400, 300));
        c.addComponent(new Resistor("R2", "R2", n1.getId(), Node.GROUND_ID, 2000, 200, 100, 500, 300));
        return c;
    }
}
