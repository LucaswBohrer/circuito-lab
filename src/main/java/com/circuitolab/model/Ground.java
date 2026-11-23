package com.circuitolab.model;

/**
 * Ligação à terra: curto entre o nó A e o nó terra (0), modelado como
 * fonte de 0 V. nodeB é sempre 0.
 */
public class Ground extends Component {
    public Ground(String id, String label, int nodeA, int x, int y) {
        super(id, label, nodeA, Node.GROUND_ID, 0.0, x, y, x, y);
    }

    @Override public String getType() { return "terra"; }
    @Override public String getUnit() { return ""; }
    @Override public boolean isVoltageBranch() { return true; }
}
