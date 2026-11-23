package com.circuitolab.model;

public class Resistor extends Component {
    public Resistor(String id, String label, int nodeA, int nodeB, double ohms,
                    int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, ohms, x1, y1, x2, y2);
    }

    @Override public String getType() { return "resistor"; }
    @Override public String getUnit() { return "Ω"; }
}
