package com.circuitolab.model;

/** Fonte de corrente independente. value = corrente de A para B (amps). */
public class CurrentSource extends Component {
    public CurrentSource(String id, String label, int nodeA, int nodeB, double amps,
                         int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, amps, x1, y1, x2, y2);
    }

    @Override public String getType() { return "fonte_corrente"; }
    @Override public String getUnit() { return "A"; }
}
