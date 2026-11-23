package com.circuitolab.model;

/** Fio ideal: curto-circuito (modelado como fonte de 0 V). */
public class Wire extends Component {
    public Wire(String id, String label, int nodeA, int nodeB,
                int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, 0.0, x1, y1, x2, y2);
    }

    @Override public String getType() { return "fio"; }
    @Override public String getUnit() { return ""; }
    @Override public boolean isVoltageBranch() { return true; }
}
