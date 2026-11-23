package com.circuitolab.ui;

import com.circuitolab.io.CircuitFileIO;
import com.circuitolab.io.CsvExporter;
import com.circuitolab.io.ReportExporter;
import com.circuitolab.model.Circuit;
import com.circuitolab.solver.MnaSolver;
import com.circuitolab.solver.Solution;
import com.circuitolab.solver.SolverException;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Janela principal: menu, barra de ferramentas, canvas e painel de resultados. */
public class MainFrame extends JFrame {
    private Circuit circuit;
    private final CircuitCanvas canvas;
    private final ResultsPanel resultsPanel;
    private final JLabel statusBar;
    private Path currentFile;

    public MainFrame() {
        super("CircuitoLab — Simulador de Circuitos Elétricos");
        this.circuit = new Circuit("Novo circuito");
        this.canvas = new CircuitCanvas(circuit);
        this.resultsPanel = new ResultsPanel();
        this.statusBar = new JLabel("Pronto. Escolha uma ferramenta e clique-arraste no canvas para desenhar.");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(resultsPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
        resultsPanel.setPreferredSize(new java.awt.Dimension(320, 0));
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu arquivo = new JMenu("Arquivo");
        arquivo.add(item("Novo", e -> novoCircuito()));
        arquivo.add(item("Abrir...", e -> abrir()));
        arquivo.add(item("Salvar", e -> salvar()));
        arquivo.add(item("Salvar como...", e -> salvarComo()));
        arquivo.addSeparator();
        arquivo.add(item("Exportar CSV...", e -> exportarCsv()));
        arquivo.add(item("Exportar relatório TXT...", e -> exportarRelatorio()));
        arquivo.addSeparator();
        arquivo.add(item("Sair", e -> dispose()));
        bar.add(arquivo);

        JMenu exemplos = new JMenu("Exemplos");
        exemplos.add(item("Divisor de Tensão", e -> carregarExemplo(Examples.divisorTensao())));
        exemplos.add(item("Ponte de Wheatstone", e -> carregarExemplo(Examples.ponteWheatstone())));
        exemplos.add(item("Resistores em Paralelo", e -> carregarExemplo(Examples.paralelo())));
        bar.add(exemplos);

        JMenu ajuda = new JMenu("Ajuda");
        ajuda.add(item("Sobre", e -> JOptionPane.showMessageDialog(this,
            "CircuitoLab v1.0\nSimulador de circuitos DC (Análise Nodal Modificada).\n"
            + "Java puro (JDK 21, sem dependências).\n\n"
            + "Desenhe com clique-arrastar, duplo clique edita o valor,\n"
            + "Ctrl+R resolve o circuito.",
            "Sobre o CircuitoLab", JOptionPane.INFORMATION_MESSAGE)));
        bar.add(ajuda);
        return bar;
    }

    private JMenuItem item(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        return item;
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        ButtonGroup group = new ButtonGroup();
        addToolButton(bar, group, "Resistor", CircuitCanvas.Tool.RESISTOR, true);
        addToolButton(bar, group, "Fonte V", CircuitCanvas.Tool.FONTE_TENSAO, false);
        addToolButton(bar, group, "Fonte I", CircuitCanvas.Tool.FONTE_CORRENTE, false);
        addToolButton(bar, group, "Capacitor", CircuitCanvas.Tool.CAPACITOR, false);
        addToolButton(bar, group, "Indutor", CircuitCanvas.Tool.INDUTOR, false);
        addToolButton(bar, group, "Fio", CircuitCanvas.Tool.FIO, false);
        addToolButton(bar, group, "Terra", CircuitCanvas.Tool.TERRA, false);
        addToolButton(bar, group, "Mover", CircuitCanvas.Tool.MOVER, false);
        addToolButton(bar, group, "Apagar", CircuitCanvas.Tool.APAGAR, false);
        bar.addSeparator();
        JButton resolver = new JButton("Resolver (Ctrl+R)");
        resolver.addActionListener(e -> resolver());
        bar.add(resolver);
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control R"), "resolver");
        getRootPane().getActionMap().put("resolver", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { resolver(); }
        });
        return bar;
    }

    private void addToolButton(JToolBar bar, ButtonGroup group, String text,
                               CircuitCanvas.Tool tool, boolean selected) {
        JToggleButton b = new JToggleButton(text, selected);
        b.addActionListener(e -> canvas.setTool(tool));
        group.add(b);
        bar.add(b);
    }

    private void resolver() {
        try {
            Solution solution = MnaSolver.solve(circuit);
            canvas.setSolution(solution);
            resultsPanel.setResults(circuit, solution);
            statusBar.setText("Circuito resolvido: " + circuit.getComponents().size()
                + " componentes, " + solution.getNodeVoltages().size() + " nós.");
        } catch (SolverException e) {
            canvas.clearSolution();
            resultsPanel.clear();
            statusBar.setText("Erro: " + e.getMessage());
            JOptionPane.showMessageDialog(this, e.getMessage(),
                "Não foi possível resolver", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void novoCircuito() {
        circuit = new Circuit("Novo circuito");
        currentFile = null;
        canvas.setCircuit(circuit);
        resultsPanel.clear();
        statusBar.setText("Novo circuito criado.");
    }

    private void carregarExemplo(Circuit exemplo) {
        circuit = exemplo;
        currentFile = null;
        canvas.setCircuit(circuit);
        resultsPanel.clear();
        statusBar.setText("Exemplo carregado: " + exemplo.getName() + ". Prima Resolver (Ctrl+R).");
    }

    private JFileChooser chooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("CircuitoLab JSON (*.json)", "json"));
        return chooser;
    }

    private void abrir() {
        JFileChooser chooser = chooser("Abrir circuito");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            circuit = CircuitFileIO.load(chooser.getSelectedFile().toPath());
            currentFile = chooser.getSelectedFile().toPath();
            canvas.setCircuit(circuit);
            resultsPanel.clear();
            statusBar.setText("Aberto: " + currentFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir: " + e.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void salvar() {
        if (currentFile == null) { salvarComo(); return; }
        try {
            CircuitFileIO.save(circuit, currentFile);
            statusBar.setText("Guardado em: " + currentFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível guardar: " + e.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void salvarComo() {
        JFileChooser chooser = chooser("Guardar circuito");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        if (!path.toString().endsWith(".json")) path = path.resolveSibling(path.getFileName() + ".json");
        currentFile = path;
        salvar();
    }

    private void exportarCsv() {
        try {
            Solution solution = MnaSolver.solve(circuit);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Exportar CSV");
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            CsvExporter.export(circuit, solution, chooser.getSelectedFile().toPath());
            statusBar.setText("CSV exportado.");
        } catch (SolverException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível resolver", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível exportar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarRelatorio() {
        try {
            Solution solution = MnaSolver.solve(circuit);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Exportar relatório TXT");
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            ReportExporter.export(circuit, solution, chooser.getSelectedFile().toPath());
            statusBar.setText("Relatório exportado.");
        } catch (SolverException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível resolver", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Não foi possível exportar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
