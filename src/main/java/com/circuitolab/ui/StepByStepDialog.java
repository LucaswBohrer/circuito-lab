package com.circuitolab.ui;

import com.circuitolab.model.Circuit;
import com.circuitolab.solver.StepByStep;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/**
 * Diálogo do passo a passo, com duas abas: "Simples" (padrão, abre
 * nela — linguagem de leigo, sem matriz) e "Completa (com matriz)".
 * Copiar copia o texto da aba selecionada; Exportar PDF leva a versão
 * simples primeiro e a completa como apêndice.
 */
public class StepByStepDialog extends JDialog {
    public StepByStepDialog(JFrame owner, String title, String text) {
        this(owner, title, text, null, null);
    }

    public StepByStepDialog(JFrame owner, String title, String text,
                            Circuit circuit, StepByStep steps) {
        super(owner, title, true);
        setSize(760, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton copy = new JButton("Copiar");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(area.getText()), null));
        JButton exportPdf = new JButton("Exportar PDF");
        exportPdf.setEnabled(circuit != null && steps != null);
        exportPdf.addActionListener(e -> {
            if (circuit != null && steps != null) {
                MainFrame.exportPdfDialog(this, circuit, steps);
            }
        });
        JButton close = new JButton("Fechar");
        close.addActionListener(e -> dispose());
        buttons.add(copy);
        buttons.add(exportPdf);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
    }

    /** Diálogo com abas: versão simples (padrão) e versão completa. */
    public StepByStepDialog(JFrame owner, String title, Circuit circuit, StepByStep steps) {
        super(owner, title, true);
        setSize(760, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JTextArea simpleArea = new JTextArea(steps.formatSimple());
        simpleArea.setEditable(false);
        simpleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        simpleArea.setCaretPosition(0);

        JTextArea fullArea = new JTextArea(steps.format());
        fullArea.setEditable(false);
        fullArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fullArea.setCaretPosition(0);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Simples", new JScrollPane(simpleArea));
        tabs.addTab("Completa (com matriz)", new JScrollPane(fullArea));
        tabs.setSelectedIndex(0);
        add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton copy = new JButton("Copiar");
        copy.addActionListener(e -> {
            JTextArea current = tabs.getSelectedIndex() == 0 ? simpleArea : fullArea;
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(current.getText()), null);
        });
        JButton exportPdf = new JButton("Exportar PDF");
        exportPdf.addActionListener(e -> MainFrame.exportPdfDialog(this, circuit, steps));
        JButton close = new JButton("Fechar");
        close.addActionListener(e -> dispose());
        buttons.add(copy);
        buttons.add(exportPdf);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
    }
}
