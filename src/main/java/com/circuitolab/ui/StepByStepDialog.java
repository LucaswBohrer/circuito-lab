package com.circuitolab.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Diálogo do passo a passo: texto monoespaçado com scroll, Copiar e Fechar. */
public class StepByStepDialog extends JDialog {
    public StepByStepDialog(JFrame owner, String title, String text) {
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
        JButton close = new JButton("Fechar");
        close.addActionListener(e -> dispose());
        buttons.add(copy);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
    }
}
