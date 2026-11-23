package com.circuitolab.io;

import com.circuitolab.model.Capacitor;
import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.model.CurrentSource;
import com.circuitolab.model.Ground;
import com.circuitolab.model.Inductor;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;
import com.circuitolab.model.VoltageSource;
import com.circuitolab.model.Wire;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Guarda e carrega circuitos no formato JSON próprio do CircuitoLab. */
public final class CircuitFileIO {
    private CircuitFileIO() {}

    public static void save(Circuit circuit, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"name\": \"").append(JsonParser.escape(circuit.getName())).append("\",\n");
        sb.append("  \"nodes\": [");
        List<Node> nodes = circuit.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (i > 0) sb.append(", ");
            sb.append("{\"id\": ").append(n.getId())
              .append(", \"x\": ").append(n.getX())
              .append(", \"y\": ").append(n.getY()).append("}");
        }
        sb.append("],\n  \"components\": [");
        List<Component> comps = circuit.getComponents();
        for (int i = 0; i < comps.size(); i++) {
            Component c = comps.get(i);
            if (i > 0) sb.append(", ");
            sb.append("\n    {\"type\": \"").append(c.getType())
              .append("\", \"id\": \"").append(JsonParser.escape(c.getId()))
              .append("\", \"label\": \"").append(JsonParser.escape(c.getLabel()))
              .append("\", \"nodeA\": ").append(c.getNodeA())
              .append(", \"nodeB\": ").append(c.getNodeB())
              .append(", \"value\": ").append(c.getValue())
              .append(", \"x1\": ").append(c.getX1())
              .append(", \"y1\": ").append(c.getY1())
              .append(", \"x2\": ").append(c.getX2())
              .append(", \"y2\": ").append(c.getY2()).append("}");
        }
        if (!comps.isEmpty()) sb.append("\n  ");
        sb.append("]\n}\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static Circuit load(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed;
        try {
            parsed = JsonParser.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IOException("Ficheiro JSON inválido: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root)) throw new IOException("JSON inválido: esperado um objeto.");
        String name = str(root.get("name"), "Circuito");
        Circuit circuit = new Circuit(name);

        Object nodesObj = root.get("nodes");
        if (nodesObj instanceof List<?> nodes) {
            for (Object o : nodes) {
                if (!(o instanceof Map<?, ?> m)) continue;
                int id = num(m.get("id"), 0).intValue();
                if (id == Node.GROUND_ID) continue; // o terra já existe
                circuit.addNodeWithId(new Node(id, num(m.get("x"), 0).intValue(), num(m.get("y"), 0).intValue()));
            }
        }
        Object compsObj = root.get("components");
        if (compsObj instanceof List<?> comps) {
            for (Object o : comps) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String type = str(m.get("type"), "");
                String id = str(m.get("id"), "?");
                String label = str(m.get("label"), id);
                int nodeA = num(m.get("nodeA"), 0).intValue();
                int nodeB = num(m.get("nodeB"), 0).intValue();
                double value = num(m.get("value"), 0).doubleValue();
                int x1 = num(m.get("x1"), 0).intValue();
                int y1 = num(m.get("y1"), 0).intValue();
                int x2 = num(m.get("x2"), 0).intValue();
                int y2 = num(m.get("y2"), 0).intValue();
                Component c = switch (type) {
                    case "resistor" -> new Resistor(id, label, nodeA, nodeB, value, x1, y1, x2, y2);
                    case "fonte_tensao" -> new VoltageSource(id, label, nodeA, nodeB, value, x1, y1, x2, y2);
                    case "fonte_corrente" -> new CurrentSource(id, label, nodeA, nodeB, value, x1, y1, x2, y2);
                    case "capacitor" -> new Capacitor(id, label, nodeA, nodeB, value, x1, y1, x2, y2);
                    case "indutor" -> new Inductor(id, label, nodeA, nodeB, value, x1, y1, x2, y2);
                    case "fio" -> new Wire(id, label, nodeA, nodeB, x1, y1, x2, y2);
                    case "terra" -> new Ground(id, label, nodeA, x1, y1);
                    default -> throw new IOException("Tipo de componente desconhecido: " + type);
                };
                circuit.addComponent(c);
            }
        }
        return circuit;
    }

    private static String str(Object o, String fallback) {
        return o instanceof String s ? s : fallback;
    }

    private static Number num(Object o, double fallback) {
        return o instanceof Number n ? n : fallback;
    }
}
