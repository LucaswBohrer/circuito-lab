package com.circuitolab.io;

import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.solver.Solution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Exporta o resultado da análise DC num relatório de texto. */
public final class ReportExporter {
    private ReportExporter() {}

    public static void export(Circuit circuit, Solution solution, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("CircuitoLab — Relatório de Análise DC\n");
        sb.append("Circuito: ").append(circuit.getName()).append("\n");
        sb.append("========================================\n\n");
        sb.append("Tensões nos nós:\n");
        for (var e : solution.getNodeVoltages().entrySet()) {
            sb.append(String.format(Locale.US, "  Nó %-4d %12.6f V%n", e.getKey(), e.getValue()));
        }
        sb.append("\nCorrentes e potências (corrente de A para B; potência absorvida):\n");
        for (Component c : circuit.getComponents()) {
            sb.append(String.format(Locale.US, "  %-6s %-14s %12.6f A  %12.6f W%n",
                c.getLabel(), "(" + c.getType() + ")",
                solution.currentOf(c.getId()), solution.powerOf(c.getId())));
        }
        sb.append("\nNotas: capacitor em DC = circuito aberto; indutor e fio em DC = curto.\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /** O mesmo relatório como String (usado pelo CLI). */
    public static String render(Circuit circuit, Solution solution) {
        StringBuilder sb = new StringBuilder();
        sb.append("Circuito: ").append(circuit.getName()).append("\n");
        sb.append("Tensões nos nós:\n");
        for (var e : solution.getNodeVoltages().entrySet()) {
            sb.append(String.format(Locale.US, "  Nó %-4d %12.6f V%n", e.getKey(), e.getValue()));
        }
        sb.append("Componentes (corrente A -> B; potência absorvida):\n");
        for (Component c : circuit.getComponents()) {
            sb.append(String.format(Locale.US, "  %-6s %-14s %12.6f A  %12.6f W%n",
                c.getLabel(), "(" + c.getType() + ")",
                solution.currentOf(c.getId()), solution.powerOf(c.getId())));
        }
        return sb.toString();
    }
}
