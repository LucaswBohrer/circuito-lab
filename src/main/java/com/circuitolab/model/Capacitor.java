package com.circuitolab.model;

/** Capacitor. Em análise DC é um circuito aberto (corrente zero). */
public class Capacitor extends Component {
    public Capacitor(String id, String label, int nodeA, int nodeB, double farads,
                     int x1, int y1, int x2, int y2) {
        super(id, label, nodeA, nodeB, farads, x1, y1, x2, y2);
    }

    @Override public String getType() { return "capacitor"; }
    @Override public String getUnit() { return "F"; }
    @Override public boolean isOpenInDc() { return true; }
}
