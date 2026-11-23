package com.circuitolab.model;

/** Fonte de tensão independente. value = V(A) - V(B). */
public class VoltageSource extends Component {
    public VoltageSource(String id, String label, int nodeA, int nodeB, double volts,
                         int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, volts, x1, y1, x2, y2);
    }

    @Override public String getType() { return "fonte_tensao"; }
    @Override public String getUnit() { return "V"; }
    @Override public boolean isVoltageBranch() { return true; }
}
