package com.circuitolab.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolução de sistema linear A·x = b por eliminação de Gauss com
 * pivotamento parcial.
 *
 * solveWithTrace() devolve, além da solução, o rasto completo do cálculo:
 * a matriz aumentada inicial, uma cópia após cada coluna eliminada (com
 * nota de troca de linhas quando há pivotamento) e os passos da
 * retrosubstituição. solve() delega para ele e devolve só a solução.
 */
public final class MatrixSolver {
    private MatrixSolver() {}

    /** Rasto da eliminação de Gauss: matrizes, notas e retrosubstituição. */
    public static final class Trace {
        private final double[][] initialAugmented;
        private final List<double[][]> steps;
        private final List<String> stepNotes;
        private final List<String> backSubstitution;
        private final double[] solution;

        Trace(double[][] initialAugmented, List<double[][]> steps,
              List<String> stepNotes, List<String> backSubstitution, double[] solution) {
            this.initialAugmented = initialAugmented;
            this.steps = steps;
            this.stepNotes = stepNotes;
            this.backSubstitution = backSubstitution;
            this.solution = solution;
        }

        /** Matriz aumentada [A | b] antes de qualquer eliminação. */
        public double[][] getInitialAugmented() { return copy(initialAugmented); }
        /** Uma cópia da matriz aumentada após cada coluna eliminada. */
        public List<double[][]> getSteps() {
            List<double[][]> out = new ArrayList<>();
            for (double[][] s : steps) out.add(copy(s));
            return out;
        }
        /** Nota de cada passo (coluna eliminada, troca de linhas, fator). */
        public List<String> getStepNotes() { return List.copyOf(stepNotes); }
        /** Passos da retrosubstituição, um por incógnita (da última à primeira). */
        public List<String> getBackSubstitution() { return List.copyOf(backSubstitution); }
        public double[] getSolution() { return solution.clone(); }

        private static double[][] copy(double[][] m) {
            double[][] out = new double[m.length][];
            for (int i = 0; i < m.length; i++) out[i] = m[i].clone();
            return out;
        }
    }

    public static double[] solve(double[][] a, double[] b) throws SolverException {
        return solveWithTrace(a, b).getSolution();
    }

    public static Trace solveWithTrace(double[][] a, double[] b) throws SolverException {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        double[][] initial = new double[n][];
        for (int i = 0; i < n; i++) initial[i] = m[i].clone();

        List<double[][]> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (int col = 0; col < n; col++) {
            int pivot = col;
            double best = Math.abs(m[col][col]);
            for (int r = col + 1; r < n; r++) {
                double v = Math.abs(m[r][col]);
                if (v > best) { best = v; pivot = r; }
            }
            if (best < 1e-12) {
                throw new SolverException(
                    "Sistema singular: verifique malha de fontes de tensão, "
                    + "fonte de tensão em curto-circuito ou nó flutuante no circuito.");
            }
            StringBuilder note = new StringBuilder("Coluna " + (col + 1) + ": pivô = linha "
                + (pivot + 1) + " (" + fmtTrace(m[pivot][col]) + ")");
            if (pivot != col) {
                double[] tmp = m[pivot];
                m[pivot] = m[col];
                m[col] = tmp;
                note.append(" — troca das linhas ").append(col + 1).append(" e ").append(pivot + 1);
            }
            for (int r = col + 1; r < n; r++) {
                double f = m[r][col] / m[col][col];
                if (f == 0.0) continue;
                note.append("; L").append(r + 1).append(" -= ").append(fmtTrace(f))
                    .append(" x L").append(col + 1);
                for (int c = col; c <= n; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
            // Limpa resíduos de arredondamento abaixo do pivô.
            for (int r = col + 1; r < n; r++) m[r][col] = 0.0;
            double[][] snapshot = new double[n][];
            for (int i = 0; i < n; i++) snapshot[i] = m[i].clone();
            steps.add(snapshot);
            notes.add(note.toString());
        }

        double[] x = new double[n];
        List<String> back = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            double s = m[i][n];
            StringBuilder step = new StringBuilder("x" + (i + 1) + " = ("
                + fmtTrace(m[i][n]));
            for (int j = i + 1; j < n; j++) {
                s -= m[i][j] * x[j];
                step.append(" - (").append(fmtTrace(m[i][j])).append(" x ")
                    .append(fmtTrace(x[j])).append(")");
            }
            step.append(") / ").append(fmtTrace(m[i][i])).append(" = ").append(fmtTrace(s / m[i][i]));
            x[i] = s / m[i][i];
            back.add(step.toString());
        }
        return new Trace(initial, steps, notes, back, x);
    }

    private static String fmtTrace(double v) {
        if (Math.abs(v) < 0.0000005) return "0.000000";
        return String.format(java.util.Locale.US, "%.6f", v);
    }
}
