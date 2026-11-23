package com.circuitolab.solver;

import com.circuitolab.model.Capacitor;
import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.model.CurrentSource;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;

import java.util.List;
import java.util.Locale;

/**
 * Resolução passo a passo da análise DC: incógnitas, equações nodais (LKC),
 * sistema MNA A·x = b, eliminação de Gauss com o rasto completo,
 * retrosubstituição, resultado final e, quando o circuito contém um
 * triângulo de resistores, a conversão Delta→Estrela.
 *
 * O texto é gerado por format() e é o mesmo na GUI, no CLI e no relatório TXT.
 */
public class StepByStep {
    private final Circuit circuit;
    private final List<String> unknowns;
    private final double[][] matrixA;
    private final double[] vectorB;
    private final List<String> equations;
    private final MatrixSolver.Trace trace;
    private final Solution solution;
    private final List<String> deltaSection;

    public StepByStep(Circuit circuit, List<String> unknowns, double[][] matrixA,
                      double[] vectorB, List<String> equations,
                      MatrixSolver.Trace trace, Solution solution,
                      List<String> deltaSection) {
        this.circuit = circuit;
        this.unknowns = List.copyOf(unknowns);
        this.matrixA = copy(matrixA);
        this.vectorB = vectorB.clone();
        this.equations = List.copyOf(equations);
        this.trace = trace;
        this.solution = solution;
        this.deltaSection = List.copyOf(deltaSection);
    }

    public List<String> getUnknowns() { return unknowns; }
    public double[][] getMatrixA() { return copy(matrixA); }
    public double[] getVectorB() { return vectorB.clone(); }
    public List<String> getEquations() { return equations; }
    public MatrixSolver.Trace getTrace() { return trace; }
    public Solution getSolution() { return solution; }
    public List<String> getDeltaSection() { return deltaSection; }

    /** Formata um número com 6 casas decimais, sem notação científica. */
    public static String fmt(double v) {
        if (v == 0.0 || Math.abs(v) < 0.0000005) return "0.000000";
        if (Math.abs(v) >= 1000000.0 || Math.abs(v) < 0.000001) {
            return String.format(Locale.US, "%.6e", v);
        }
        return String.format(Locale.US, "%.6f", v);
    }

    /** Texto completo do passo a passo, pronto a mostrar, imprimir ou guardar. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("CircuitoLab — Passo a passo (Análise DC por MNA)\n");
        sb.append("Circuito: ").append(circuit.getName()).append("\n");
        sb.append("============================================================\n\n");

        sb.append("1. Incógnitas (").append(unknowns.size()).append(")\n");
        for (int i = 0; i < unknowns.size(); i++) {
            sb.append("   x").append(i + 1).append(" = ").append(unknowns.get(i)).append("\n");
        }
        sb.append("   Nó 0 = terra (0 V, referência — não é incógnita).\n\n");

        sb.append("2. Equações (Lei de Kirchhoff das Correntes: a soma das\n");
        sb.append("   correntes que saem de cada nó é zero; corrente de um\n");
        sb.append("   resistor: I = (Va - Vb) / R)\n");
        for (String eq : equations) sb.append("   ").append(eq).append("\n");
        sb.append("\n");

        sb.append("3. Sistema A . x = b (matriz aumentada [A | b])\n");
        sb.append(renderAugmented(trace.getInitialAugmented()));
        sb.append("\n");

        sb.append("4. Eliminação de Gauss com pivotamento parcial\n");
        List<double[][]> steps = trace.getSteps();
        List<String> notes = trace.getStepNotes();
        for (int i = 0; i < steps.size(); i++) {
            sb.append("   Passo ").append(i + 1).append(" — ").append(notes.get(i)).append("\n");
            sb.append(renderAugmented(steps.get(i)));
        }
        sb.append("\n");

        sb.append("5. Retrosubstituição (da última incógnita para a primeira)\n");
        for (String s : trace.getBackSubstitution()) sb.append("   ").append(s).append("\n");
        sb.append("\n");

        sb.append("6. Resultado final\n");
        sb.append("   Tensões nos nós:\n");
        for (var e : solution.getNodeVoltages().entrySet()) {
            sb.append(String.format(Locale.US, "     Nó %-4d %12s V%n", e.getKey(), fmt(e.getValue())));
        }
        sb.append("   Correntes e potências (corrente de A para B; potência absorvida):\n");
        for (Component c : circuit.getComponents()) {
            sb.append("     ").append(componentResultLine(c)).append("\n");
        }
        sb.append("\n");

        if (!deltaSection.isEmpty()) {
            sb.append("7. Conversão Delta (Triângulo) -> Estrela\n");
            for (String line : deltaSection) sb.append("   ").append(line).append("\n");
            sb.append("\n");
        }

        sb.append("Notas: capacitor em DC = circuito aberto; indutor e fio em DC = curto.\n");
        return sb.toString();
    }

    private String componentResultLine(Component c) {
        double va = solution.voltageOf(c.getNodeA());
        double vb = solution.voltageOf(c.getNodeB());
        double i = solution.currentOf(c.getId());
        double p = solution.powerOf(c.getId());
        String formula;
        if (c instanceof Resistor r) {
            formula = String.format(Locale.US, "I = (%.6f - %.6f) / %.6f = %s A",
                va, vb, r.getValue(), fmt(i));
        } else if (c instanceof CurrentSource) {
            formula = "I = valor da fonte = " + fmt(i) + " A";
        } else if (c instanceof Capacitor) {
            formula = "I = 0 A (capacitor aberto em DC)";
        } else {
            formula = "I (ramo de tensão) = " + fmt(i) + " A";
        }
        return String.format(Locale.US, "%-6s %-14s %12s A  %12s W  |  %s",
            c.getLabel(), "(" + c.getType() + ")", fmt(i), fmt(p), formula);
    }

    private String renderAugmented(double[][] m) {
        StringBuilder sb = new StringBuilder();
        int n = vectorB.length;
        // Cabeçalho com as incógnitas.
        sb.append("   [");
        for (int j = 0; j < n; j++) sb.append(String.format(" %12s", "x" + (j + 1)));
        sb.append(" | ").append(String.format("%12s", "b")).append(" ]\n");
        for (int i = 0; i < n; i++) {
            sb.append("   [");
            for (int j = 0; j < n; j++) sb.append(String.format(" %12s", fmt(m[i][j])));
            sb.append(" | ").append(String.format("%12s", fmt(m[i][n]))).append(" ]\n");
        }
        return sb.toString();
    }

    private static double[][] copy(double[][] m) {
        double[][] out = new double[m.length][];
        for (int i = 0; i < m.length; i++) out[i] = m[i].clone();
        return out;
    }

    /** Rótulo de tensão de um nó para as equações: "V(N2)" ou "0" para o terra. */
    static String vLabel(int nodeId) {
        return nodeId == Node.GROUND_ID ? "0" : "V(N" + nodeId + ")";
    }
}
