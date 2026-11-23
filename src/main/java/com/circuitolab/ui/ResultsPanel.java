package com.circuitolab.ui;

import com.circuitolab.model.Circuit;
import com.circuitolab.model.Component;
import com.circuitolab.solver.Solution;

import java.awt.BorderLayout;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Painel lateral com as tabelas de resultados (nós e componentes). */
public class ResultsPanel extends JPanel {
    private final DefaultTableModel nodesModel =
        new DefaultTableModel(new Object[]{"Nó", "Tensão (V)"}, 0);
    private final DefaultTableModel compsModel =
        new DefaultTableModel(new Object[]{"Comp.", "Corrente (A)", "Potência (W)"}, 0);

    public ResultsPanel() {
        super(new BorderLayout());
        JTable nodesTable = new JTable(nodesModel);
        JTable compsTable = new JTable(compsModel);
        nodesTable.setFillsViewportHeight(true);
        compsTable.setFillsViewportHeight(true);
        JPanel tables = new JPanel(new BorderLayout());
        tables.add(new JScrollPane(nodesTable), BorderLayout.NORTH);
        tables.add(new JScrollPane(compsTable), BorderLayout.CENTER);
        add(tables, BorderLayout.CENTER);
    }

    public void setResults(Circuit circuit, Solution solution) {
        nodesModel.setRowCount(0);
        for (var e : solution.getNodeVoltages().entrySet()) {
            nodesModel.addRow(new Object[]{e.getKey(), fmt(e.getValue())});
        }
        compsModel.setRowCount(0);
        for (Component c : circuit.getComponents()) {
            compsModel.addRow(new Object[]{
                c.getLabel(),
                fmt(solution.currentOf(c.getId())),
                fmt(solution.powerOf(c.getId()))
            });
        }
    }

    public void clear() {
        nodesModel.setRowCount(0);
        compsModel.setRowCount(0);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}
