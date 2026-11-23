package com.circuitolab.solver;

import java.util.Collections;
import java.util.Map;

/**
 * Resultado da análise DC: tensão de cada nó (V), corrente de cada
 * componente (A, do nó A para o nó B) e potência absorvida (W).
 */
public class Solution {
    private final Map<Integer, Double> nodeVoltages;
    private final Map<String, Double> currents;
    private final Map<String, Double> powers;

    public Solution(Map<Integer, Double> nodeVoltages,
                    Map<String, Double> currents,
                    Map<String, Double> powers) {
        this.nodeVoltages = Collections.unmodifiableMap(nodeVoltages);
        this.currents = Collections.unmodifiableMap(currents);
        this.powers = Collections.unmodifiableMap(powers);
    }

    public Map<Integer, Double> getNodeVoltages() { return nodeVoltages; }
    public Map<String, Double> getCurrents() { return currents; }
    public Map<String, Double> getPowers() { return powers; }

    public double voltageOf(int nodeId) { return nodeVoltages.getOrDefault(nodeId, 0.0); }
    public double currentOf(String componentId) { return currents.getOrDefault(componentId, 0.0); }
    public double powerOf(String componentId) { return powers.getOrDefault(componentId, 0.0); }
}
