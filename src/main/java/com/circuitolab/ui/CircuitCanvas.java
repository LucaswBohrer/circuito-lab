package com.circuitolab.ui;

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
import com.circuitolab.solver.Solution;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Canvas de desenho do circuito: grade com snap de 20 px, desenho de
 * componentes por clique-arrastar, mover e apagar, edição por duplo clique.
 * Um clique simples sem arrastar não cria nó nem componente.
 */
public class CircuitCanvas extends JPanel {
    public enum Tool {
        RESISTOR, FONTE_TENSAO, FONTE_CORRENTE, CAPACITOR, INDUTOR, FIO, TERRA, MOVER, APAGAR
    }

    private static final int GRID = 20;
    private static final int SNAP_RADIUS = 12;

    private Circuit circuit;
    private Solution solution;
    private Tool tool = Tool.RESISTOR;
    private Point dragStart;
    private Point dragCurrent;
    private Node moveNode;

    public CircuitCanvas(Circuit circuit) {
        this.circuit = circuit;
        setBackground(Color.WHITE);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { onPress(e); }
            @Override public void mouseDragged(MouseEvent e) { onDrag(e); }
            @Override public void mouseReleased(MouseEvent e) { onRelease(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onDoubleClick(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public Circuit getCircuit() { return circuit; }
    public void setCircuit(Circuit circuit) {
        // Limpa nós soltos herdados do ficheiro/exemplo (ex.: de cliques antigos).
        circuit.pruneUnusedNodes();
        this.circuit = circuit;
        this.solution = null;
        repaint();
    }

    /** Remove os nós soltos do circuito atual; devolve quantos removeu. */
    public int pruneUnusedNodes() {
        int removed = circuit.pruneUnusedNodes();
        clearSolution();
        repaint();
        return removed;
    }
    public void setTool(Tool tool) { this.tool = tool; }
    public void setSolution(Solution solution) {
        this.solution = solution;
        repaint();
    }
    public void clearSolution() { this.solution = null; repaint(); }

    private Point snap(Point p) {
        return new Point(Math.round(p.x / (float) GRID) * GRID, Math.round(p.y / (float) GRID) * GRID);
    }

    private Node findNodeNear(Point p) {
        Node best = null;
        double bestDist = SNAP_RADIUS;
        for (Node n : circuit.getNodes()) {
            if (n.isGround()) continue;
            double d = p.distance(n.getX(), n.getY());
            if (d <= bestDist) { bestDist = d; best = n; }
        }
        return best;
    }

    private Node findOrCreateNode(Point p) {
        Node n = findNodeNear(p);
        return n != null ? n : circuit.addNode(p.x, p.y);
    }

    private Component findComponentNear(Point p) {
        Component best = null;
        double bestDist = 10;
        for (Component c : circuit.getComponents()) {
            double d = pointToSegment(p.x, p.y, c.getX1(), c.getY1(), c.getX2(), c.getY2());
            if (d <= bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private static double pointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = x1 + t * dx, cy = y1 + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private void onPress(MouseEvent e) {
        Point p = snap(e.getPoint());
        dragStart = p;
        dragCurrent = p;
        if (tool == Tool.TERRA) {
            Node n = findOrCreateNode(p);
            boolean already = circuit.getComponents().stream()
                .anyMatch(c -> c instanceof Ground && c.getNodeA() == n.getId());
            if (!already && n.getId() != Node.GROUND_ID) {
                circuit.addComponent(new Ground(circuit.nextId("GND"), "GND", n.getId(), p.x, p.y));
            }
            clearSolution();
            repaint();
            return;
        }
        if (tool == Tool.APAGAR) {
            Component c = findComponentNear(p);
            if (c != null) {
                int a = c.getNodeA(), b = c.getNodeB();
                circuit.removeComponent(c);
                circuit.removeNodeIfUnused(a);
                circuit.removeNodeIfUnused(b);
                clearSolution();
                repaint();
            }
            return;
        }
        if (tool == Tool.MOVER) {
            moveNode = findNodeNear(p);
            return;
        }
        // Ferramentas de componente: um clique simples NÃO cria nó.
        // O nó só é criado no mouseReleased, e só se houve arrastar de
        // verdade — senão, clicar (ou duplo clicar para editar o valor)
        // deixava um nó solto a cada clique.
    }

    private void onDrag(MouseEvent e) {
        Point p = snap(e.getPoint());
        dragCurrent = p;
        if (tool == Tool.MOVER && moveNode != null) {
            moveNode.setX(p.x);
            moveNode.setY(p.y);
            for (Component c : circuit.getComponents()) {
                if (c.getNodeA() == moveNode.getId()) c.setPosition(p.x, p.y, c.getX2(), c.getY2());
                if (c.getNodeB() == moveNode.getId()) c.setPosition(c.getX1(), c.getY1(), p.x, p.y);
            }
            clearSolution();
        }
        repaint();
    }

    private void onRelease(MouseEvent e) {
        Point p = snap(e.getPoint());
        if (tool == Tool.TERRA || tool == Tool.APAGAR || tool == Tool.MOVER) {
            moveNode = null;
            dragStart = null;
            repaint();
            return;
        }
        if (dragStart == null) return;
        // Clique sem arrastar: não faz nada (não cria nó nem componente).
        if (dragStart.equals(p)) { dragStart = null; repaint(); return; }
        // Arraste de verdade: aí sim criam-se/reutilizam-se os nós das pontas.
        Node startNode = findOrCreateNode(dragStart);
        Node endNode = findOrCreateNode(p);
        Component c = createComponent(tool, startNode, endNode, dragStart, p);
        if (c != null) circuit.addComponent(c);
        clearSolution();
        dragStart = null;
        repaint();
    }

    private Component createComponent(Tool t, Node a, Node b, Point p1, Point p2) {
        // O rótulo é igual ao id (ex.: "R1").
        String id;
        switch (t) {
            case RESISTOR -> { id = circuit.nextId("R"); return new Resistor(id, id, a.getId(), b.getId(), 1000, p1.x, p1.y, p2.x, p2.y); }
            case FONTE_TENSAO -> { id = circuit.nextId("V"); return new VoltageSource(id, id, a.getId(), b.getId(), 12, p1.x, p1.y, p2.x, p2.y); }
            case FONTE_CORRENTE -> { id = circuit.nextId("I"); return new CurrentSource(id, id, a.getId(), b.getId(), 0.01, p1.x, p1.y, p2.x, p2.y); }
            case CAPACITOR -> { id = circuit.nextId("C"); return new Capacitor(id, id, a.getId(), b.getId(), 0.000001, p1.x, p1.y, p2.x, p2.y); }
            case INDUTOR -> { id = circuit.nextId("L"); return new Inductor(id, id, a.getId(), b.getId(), 0.001, p1.x, p1.y, p2.x, p2.y); }
            case FIO -> { id = circuit.nextId("W"); return new Wire(id, id, a.getId(), b.getId(), p1.x, p1.y, p2.x, p2.y); }
            default -> { return null; }
        }
    }

    private void onDoubleClick(MouseEvent e) {
        Component c = findComponentNear(e.getPoint());
        if (c == null || c instanceof Wire || c instanceof Ground) return;
        String unit = c.getUnit();
        String input = JOptionPane.showInputDialog(this,
            "Novo valor para " + c.getLabel() + " (" + unit + "):",
            String.valueOf(c.getValue()));
        if (input == null) return;
        try {
            double v = Double.parseDouble(input.trim().replace(',', '.'));
            c.setValue(v);
            clearSolution();
            repaint();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valor inválido: " + input,
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Grade
        g2.setColor(new Color(235, 235, 235));
        for (int x = 0; x < getWidth(); x += GRID) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += GRID) g2.drawLine(0, y, getWidth(), y);

        // Pré-visualização do arrastar
        if (dragStart != null && dragCurrent != null && tool != Tool.MOVER
                && tool != Tool.APAGAR && tool != Tool.TERRA) {
            g2.setColor(Color.GRAY);
            g2.drawLine(dragStart.x, dragStart.y, dragCurrent.x, dragCurrent.y);
        }

        // Componentes
        for (Component c : circuit.getComponents()) {
            drawComponent(g2, c);
        }

        // Nós
        g2.setColor(Color.BLACK);
        for (Node n : circuit.getNodes()) {
            if (n.isGround()) continue;
            g2.fillOval(n.getX() - 3, n.getY() - 3, 6, 6);
        }

        // Resultados: tensões nos nós
        if (solution != null) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.setColor(new Color(0, 100, 0));
            for (Node n : circuit.getNodes()) {
                if (n.isGround()) continue;
                // Só mostra a tensão se a solução contiver o nó: um nó solto
                // não tem tensão calculada e nunca deve exibir "0.00 V".
                if (!solution.getNodeVoltages().containsKey(n.getId())) continue;
                double v = solution.voltageOf(n.getId());
                g2.drawString("N" + n.getId() + ": " + fmtVolt(v), n.getX() + 8, n.getY() - 8);
            }
        }
    }

    private void drawComponent(Graphics2D g2, Component c) {
        int x1 = c.getX1(), y1 = c.getY1(), x2 = c.getX2(), y2 = c.getY2();
        int mx = c.getMidX(), my = c.getMidY();
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));

        if (c instanceof Ground) {
            drawGroundSymbol(g2, x1, y1);
        } else {
            g2.drawLine(x1, y1, x2, y2);
            if (c instanceof Resistor) {
                g2.drawRect(mx - 14, my - 6, 28, 12);
            } else if (c instanceof VoltageSource) {
                g2.drawOval(mx - 11, my - 11, 22, 22);
                g2.drawString("+", mx - 3, my - 12);
                g2.drawString("−", mx - 3, my + 20);
            } else if (c instanceof CurrentSource) {
                g2.drawOval(mx - 11, my - 11, 22, 22);
                g2.drawString("↑", mx - 4, my + 4);
            } else if (c instanceof Capacitor) {
                g2.drawLine(mx - 5, my - 10, mx - 5, my + 10);
                g2.drawLine(mx + 5, my - 10, mx + 5, my + 10);
            } else if (c instanceof Inductor) {
                for (int i = -1; i <= 1; i++) {
                    g2.drawArc(mx + i * 10 - 5, my - 5, 10, 10, 180, 180);
                }
            }
            // Símbolo de terra nas pontas ligadas ao nó 0
            if (c.getNodeA() == Node.GROUND_ID) drawGroundSymbol(g2, x1, y1);
            if (c.getNodeB() == Node.GROUND_ID) drawGroundSymbol(g2, x2, y2);
        }

        // Rótulo + valor
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        String text = c.getLabel() + (c.getUnit().isEmpty() ? "" : " " + formatValue(c));
        g2.drawString(text, mx + 14, my - 8);

        // Corrente (quando há solução)
        if (solution != null && !(c instanceof Ground)) {
            g2.setColor(new Color(0, 0, 160));
            g2.drawString(fmtCurrent(solution.currentOf(c.getId())), mx + 14, my + 18);
            g2.setColor(Color.BLACK);
        }
    }

    private void drawGroundSymbol(Graphics2D g2, int x, int y) {
        g2.drawLine(x, y, x, y + 8);
        g2.drawLine(x - 10, y + 8, x + 10, y + 8);
        g2.drawLine(x - 6, y + 12, x + 6, y + 12);
        g2.drawLine(x - 3, y + 16, x + 3, y + 16);
    }

    private static String formatValue(Component c) {
        double v = c.getValue();
        if (c instanceof Resistor) {
            if (v >= 1000) return String.format(Locale.US, "%.0f kΩ", v / 1000);
            return String.format(Locale.US, "%.0f Ω", v);
        }
        if (c instanceof Capacitor) return String.format(Locale.US, "%.0f µF", v * 1e6);
        if (c instanceof Inductor) return String.format(Locale.US, "%.0f mH", v * 1000);
        return String.format(Locale.US, "%.3g %s", v, c.getUnit());
    }

    private static String fmtVolt(double v) {
        return String.format(Locale.US, "%.2f V", v);
    }

    private static String fmtCurrent(double i) {
        if (Math.abs(i) >= 1) return String.format(Locale.US, "%.3f A", i);
        if (Math.abs(i) >= 0.001) return String.format(Locale.US, "%.2f mA", i * 1000);
        return String.format(Locale.US, "%.2f µA", i * 1e6);
    }
}
