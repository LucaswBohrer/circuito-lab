package com.circuitolab.solver;

/** Resolução de sistema linear A·x = b por eliminação de Gauss com pivotamento parcial. */
public final class MatrixSolver {
    private MatrixSolver() {}

    public static double[] solve(double[][] a, double[] b) throws SolverException {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
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
            if (pivot != col) {
                double[] tmp = m[pivot];
                m[pivot] = m[col];
                m[col] = tmp;
            }
            for (int r = col + 1; r < n; r++) {
                double f = m[r][col] / m[col][col];
                if (f == 0.0) continue;
                for (int c = col; c <= n; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = m[i][n];
            for (int j = i + 1; j < n; j++) {
                s -= m[i][j] * x[j];
            }
            x[i] = s / m[i][i];
        }
        return x;
    }
}
