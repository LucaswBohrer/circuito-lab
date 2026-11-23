package com.circuitolab.model;

/** Indutor. Em análise DC é um curto-circuito (modelado como fonte de 0 V). */
public class Inductor extends Component {
    public Inductor(String id, String label, int nodeA, int nodeB, double henries,
                    int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, henries, x1, y1, x2, y2);
    }

    @Override public String getType() { return "indutor"; }
    @Override public String getUnit() { return "H"; }
    @Override public boolean isVoltageBranch() { return true; }
}
