package com.circuitolab.io;

import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.solver.Solution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Exporta o resultado da análise em CSV (nós e componentes). */
public final class CsvExporter {
    private CsvExporter() {}

    public static void export(Circuit circuit, Solution solution, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("nos\n");
        sb.append("no;tensao_V\n");
        for (var e : solution.getNodeVoltages().entrySet()) {
            sb.append(e.getKey()).append(';')
              .append(String.format(Locale.US, "%.6f", e.getValue())).append('\n');
        }
        sb.append("\ncomponentes\n");
        sb.append("id;rotulo;tipo;corrente_A;potencia_W\n");
        for (Component c : circuit.getComponents()) {
            sb.append(c.getId()).append(';').append(c.getLabel()).append(';').append(c.getType())
              .append(';').append(String.format(Locale.US, "%.6f", solution.currentOf(c.getId())))
              .append(';').append(String.format(Locale.US, "%.6f", solution.powerOf(c.getId())))
              .append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }
}
