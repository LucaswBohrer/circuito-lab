package com.circuitolab.app;

import com.circuitolab.io.CircuitFileIO;
import com.circuitolab.io.CsvExporter;
import com.circuitolab.io.ReportExporter;
import com.circuitolab.model.Circuit;
import com.circuitolab.solver.MnaSolver;
import com.circuitolab.solver.Solution;
import com.circuitolab.solver.SolverException;
import com.circuitolab.ui.MainFrame;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada.
 * Sem argumentos: abre a GUI.
 * CLI: --solve <ficheiro.json> [--csv out.csv] [--report out.txt]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length >= 2 && "--solve".equals(args[0])) {
            System.exit(runCli(args));
        }
        if (args.length > 0) {
            System.err.println("Uso: java -cp out com.circuitolab.app.Main [--solve <ficheiro.json> [--csv out.csv] [--report out.txt]]");
            System.exit(2);
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    private static int runCli(String[] args) {
        Path input = Path.of(args[1]);
        Path csv = null;
        Path report = null;
        for (int i = 2; i < args.length; i++) {
            if ("--csv".equals(args[i]) && i + 1 < args.length) csv = Path.of(args[++i]);
            else if ("--report".equals(args[i]) && i + 1 < args.length) report = Path.of(args[++i]);
            else { System.err.println("Argumento desconhecido: " + args[i]); return 2; }
        }
        try {
            Circuit circuit = CircuitFileIO.load(input);
            Solution solution = MnaSolver.solve(circuit);
            System.out.print(ReportExporter.render(circuit, solution));
            if (csv != null) {
                CsvExporter.export(circuit, solution, csv);
                System.out.println("CSV guardado em: " + csv);
            }
            if (report != null) {
                ReportExporter.export(circuit, solution, report);
                System.out.println("Relatório guardado em: " + report);
            }
            return 0;
        } catch (SolverException e) {
            System.err.println("Erro ao resolver o circuito: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            return 1;
        }
    }
}
