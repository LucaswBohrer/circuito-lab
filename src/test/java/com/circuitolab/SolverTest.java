package com.circuitolab;

import com.circuitolab.model.Capacitor;
import com.circuitolab.model.Circuit;
import com.circuitolab.model.CurrentSource;
import com.circuitolab.model.Inductor;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;
import com.circuitolab.model.VoltageSource;
import com.circuitolab.solver.MatrixSolver;
import com.circuitolab.solver.MnaSolver;
import com.circuitolab.solver.Solution;
import com.circuitolab.solver.SolverException;
import com.circuitolab.solver.StepByStep;
import com.circuitolab.ui.Examples;

/**
 * Testes do solver MNA. Runner próprio (sem JUnit): cada teste devolve
 * void e falha lançando AssertionError; o TestRunner conta PASS/FAIL.
 *
 * Nota: um circuito já tem terra explícita quando algum componente toca
 * o nó 0 — não é preciso (nem válido) adicionar um Ground com os dois
 * terminais no nó 0.
 */
public class SolverTest {
    private static final double EPS = 1e-6;

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    static void approx(double actual, double expected, String msg) {
        if (Math.abs(actual - expected) > EPS) {
            throw new AssertionError(msg + " (esperado " + expected + ", obtido " + actual + ")");
        }
    }

    /** Nó de apoio: cria nó em (x, 100). */
    private static Node node(Circuit c, int x) { return c.addNode(x, 100); }

    /** Divisor de tensão: 12 V, R1=1k (n1-n2), R2=2k (n2-terra) => Vn2=8 V, I=4 mA. */
    public static void divisorTensao() throws SolverException {
        Circuit c = new Circuit("Divisor");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 100));
        c.addComponent(new Resistor("R2", "R2", n2.getId(), Node.GROUND_ID, 2000, 200, 100, 200, 200));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n2.getId()), 8.0, "Tensão no meio do divisor");
        approx(s.voltageOf(n1.getId()), 12.0, "Tensão da fonte");
        approx(s.currentOf("R1"), 0.004, "Corrente em R1");
        approx(s.currentOf("R2"), 0.004, "Corrente em R2");
    }

    /** Resistores em paralelo: 12 V, 1k // 2k => V=12 V, I1=12 mA, I2=6 mA. */
    public static void resistoresParalelo() throws SolverException {
        Circuit c = new Circuit("Paralelo");
        Node n1 = node(c, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), Node.GROUND_ID, 1000, 100, 100, 100, 200));
        c.addComponent(new Resistor("R2", "R2", n1.getId(), Node.GROUND_ID, 2000, 120, 100, 120, 200));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n1.getId()), 12.0, "Tensão no paralelo");
        approx(s.currentOf("R1"), 0.012, "Corrente em R1 (paralelo)");
        approx(s.currentOf("R2"), 0.006, "Corrente em R2 (paralelo)");
        approx(s.currentOf("V1"), -0.018, "Corrente da fonte (fornece 18 mA)");
    }

    /** Ponte de Wheatstone equilibrada: corrente no ramo central ~ 0. */
    public static void ponteWheatstoneEquilibrada() throws SolverException {
        Circuit c = new Circuit("Ponte");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        Node n3 = node(c, 300);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 100));
        c.addComponent(new Resistor("R2", "R2", n1.getId(), n3.getId(), 1000, 100, 100, 300, 100));
        c.addComponent(new Resistor("R3", "R3", n2.getId(), Node.GROUND_ID, 1000, 200, 100, 200, 200));
        c.addComponent(new Resistor("R4", "R4", n3.getId(), Node.GROUND_ID, 1000, 300, 100, 300, 200));
        c.addComponent(new Resistor("R5", "R5", n2.getId(), n3.getId(), 1000, 200, 100, 300, 100));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n2.getId()), 6.0, "Tensão nó 2 da ponte");
        approx(s.voltageOf(n3.getId()), 6.0, "Tensão nó 3 da ponte");
        approx(s.currentOf("R5"), 0.0, "Corrente no ramo central da ponte equilibrada");
    }

    /** Fonte de corrente: 10 mA (terra -> n1) em paralelo com 1k => Vn1 = 10 V. */
    public static void fonteCorrente() throws SolverException {
        Circuit c = new Circuit("Fonte de corrente");
        Node n1 = node(c, 100);
        c.addComponent(new CurrentSource("I1", "I1", Node.GROUND_ID, n1.getId(), 0.01, 100, 0, 100, 100));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), Node.GROUND_ID, 1000, 100, 100, 100, 200));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n1.getId()), 10.0, "Tensão com fonte de corrente");
        approx(s.currentOf("R1"), 0.01, "Corrente no resistor (fonte de corrente)");
        approx(s.currentOf("I1"), 0.01, "Corrente da fonte de corrente");
    }

    /** Duas fontes: V1=12 V (n1), V2=5 V (n2), R=1k entre n1 e n2 => I = 7 mA. */
    public static void duasFontes() throws SolverException {
        Circuit c = new Circuit("Duas fontes");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new VoltageSource("V2", "V2", n2.getId(), Node.GROUND_ID, 5, 200, 100, 200, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 100));
        Solution s = MnaSolver.solve(c);
        approx(s.currentOf("R1"), 0.007, "Corrente entre as duas fontes");
        approx(s.voltageOf(n1.getId()), 12.0, "Tensão n1 (duas fontes)");
        approx(s.voltageOf(n2.getId()), 5.0, "Tensão n2 (duas fontes)");
    }

    /** Capacitor em DC: circuito aberto — só o resistor define a tensão. */
    public static void capacitorDcAberto() throws SolverException {
        Circuit c = new Circuit("Capacitor DC");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 100));
        c.addComponent(new Capacitor("C1", "C1", n2.getId(), Node.GROUND_ID, 0.000001, 200, 100, 200, 200));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n2.getId()), 12.0, "Tensão após capacitor aberto (sem carga)");
        approx(s.currentOf("C1"), 0.0, "Corrente no capacitor em DC");
        approx(s.currentOf("R1"), 0.0, "Corrente em R1 com capacitor aberto");
    }

    /** Indutor em DC: curto — fonte 12 V, R 1k, indutor para o terra. */
    public static void indutorDcCurto() throws SolverException {
        Circuit c = new Circuit("Indutor DC");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 100));
        c.addComponent(new Inductor("L1", "L1", n2.getId(), Node.GROUND_ID, 0.001, 200, 100, 200, 200));
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(n2.getId()), 0.0, "Tensão após indutor (curto em DC)");
        approx(s.currentOf("R1"), 0.012, "Corrente em R1 com indutor em curto");
        approx(s.currentOf("L1"), 0.012, "Corrente no indutor em DC");
    }

    /** Erro: circuito sem terra explícita. */
    public static void erroSemTerra() {
        Circuit c = new Circuit("Sem terra");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), n2.getId(), 12, 100, 100, 200, 100));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), n2.getId(), 1000, 100, 100, 200, 120));
        try {
            MnaSolver.solve(c);
        } catch (SolverException e) {
            check(e.getMessage().contains("terra"), "Mensagem de erro deve mencionar terra, foi: " + e.getMessage());
            return;
        }
        throw new AssertionError("Devia lançar SolverException (circuito sem terra).");
    }

    /** Erro: fonte de tensão curto-circuitada (mesmo nó nos dois terminais). */
    public static void erroFonteEmCurto() {
        Circuit c = new Circuit("Fonte em curto");
        Node n1 = node(c, 100);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), n1.getId(), 12, 100, 100, 100, 100));
        c.addComponent(new Resistor("R1", "R1", n1.getId(), Node.GROUND_ID, 1000, 100, 100, 100, 200));
        try {
            MnaSolver.solve(c);
        } catch (SolverException e) {
            check(e.getMessage().contains("curto"), "Mensagem de erro deve mencionar curto, foi: " + e.getMessage());
            return;
        }
        throw new AssertionError("Devia lançar SolverException (fonte em curto).");
    }

    /** Erro: circuito vazio. */
    public static void erroCircuitoVazio() {
        Circuit c = new Circuit("Vazio");
        try {
            MnaSolver.solve(c);
        } catch (SolverException e) {
            check(e.getMessage().contains("vazio"), "Mensagem de erro deve mencionar vazio, foi: " + e.getMessage());
            return;
        }
        throw new AssertionError("Devia lançar SolverException (circuito vazio).");
    }

    /** Nó de apoio por coordenadas (usado nos testes dos exemplos). */
    private static Node nodeAt(Circuit c, int x, int y) {
        for (Node n : c.getNodes()) {
            if (n.getX() == x && n.getY() == y) return n;
        }
        throw new AssertionError("Nó não encontrado em (" + x + ", " + y + ").");
    }

    /** Série: 12 V, 1k + 2k + 3k => I = 2 mA; nós intermédios 10 V e 6 V. */
    public static void resistoresSerie() throws SolverException {
        Circuit c = Examples.serie();
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(nodeAt(c, 200, 100).getId()), 12.0, "Tensão da fonte (série)");
        approx(s.voltageOf(nodeAt(c, 400, 100).getId()), 10.0, "Tensão após R1 (série)");
        approx(s.voltageOf(nodeAt(c, 600, 100).getId()), 6.0, "Tensão após R2 (série)");
        approx(s.currentOf("R1"), 0.002, "Corrente total (série)");
        approx(s.currentOf("R2"), 0.002, "Corrente em R2 (série)");
        approx(s.currentOf("R3"), 0.002, "Corrente em R3 (série)");
    }

    /**
     * Delta-Estrela: 12 V, série 10 Ω, delta 30 Ω, carga 15 Ω.
     * Total 45 Ω => I = 0,2667 A; VA = 28/3 V; VB = 20/3 V; VC = 4 V.
     */
    public static void deltaEstrela() throws SolverException {
        Circuit c = Examples.deltaEstrela();
        Solution s = MnaSolver.solve(c);
        approx(s.voltageOf(nodeAt(c, 400, 100).getId()), 28.0 / 3.0, "Tensão VA (delta-estrela)");
        approx(s.voltageOf(nodeAt(c, 600, 200).getId()), 20.0 / 3.0, "Tensão VB (delta-estrela)");
        approx(s.voltageOf(nodeAt(c, 400, 300).getId()), 4.0, "Tensão VC (delta-estrela)");
        approx(s.currentOf("R1"), 12.0 / 45.0, "Corrente total (delta-estrela)");
        approx(s.currentOf("R5"), 12.0 / 45.0, "Corrente na carga (delta-estrela)");
        // A secção Delta→Estrela tem de existir, com R_Y = 10 Ω.
        StepByStep steps = MnaSolver.solveWithSteps(c);
        check(!steps.getDeltaSection().isEmpty(), "Passo a passo devia ter secção Delta→Estrela.");
        boolean temRy10 = false;
        for (String line : steps.getDeltaSection()) {
            if (line.contains("10.000000")) temRy10 = true;
        }
        check(temRy10, "Secção Delta→Estrela devia mostrar R_Y = 10.000000 Ω.");
    }

    /** pruneUnusedNodes: remove o nó solto, mantém o nó usado e o terra. */
    public static void pruneUnusedNodes() {
        Circuit c = new Circuit("Prune");
        Node usado = c.addNode(100, 100);
        Node solto = c.addNode(200, 200);
        c.addComponent(new VoltageSource("V1", "V1", usado.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        int removed = c.pruneUnusedNodes();
        check(removed == 1, "Devia remover 1 nó solto, removeu " + removed + ".");
        check(c.getNode(solto.getId()) == null, "Nó solto devia ter sido removido.");
        check(c.getNode(usado.getId()) != null, "Nó usado não devia ter sido removido.");
        check(c.getNode(Node.GROUND_ID) != null, "Terra não devia ter sido removida.");
        check(c.pruneUnusedNodes() == 0, "Segunda limpeza devia remover 0 nós.");
    }

    /** Traço da eliminação de Gauss no divisor: solução certa, matriz final triangular. */
    public static void traceEliminacaoGauss() throws SolverException {
        Circuit c = Examples.divisorTensao();
        StepByStep steps = MnaSolver.solveWithSteps(c);
        MatrixSolver.Trace trace = steps.getTrace();
        check(steps.getUnknowns().size() == 3, "Divisor devia ter 3 incógnitas.");
        check(steps.getUnknowns().contains("V(N2)"), "Incógnitas deviam incluir V(N2).");
        check(steps.getUnknowns().contains("I(V1)"), "Incógnitas deviam incluir I(V1).");
        check(!steps.getEquations().isEmpty(), "Passo a passo devia ter equações.");
        check(!trace.getSteps().isEmpty(), "Traço devia ter pelo menos 1 passo de eliminação.");
        check(trace.getSteps().size() == trace.getStepNotes().size(),
            "Cada passo devia ter a sua nota.");
        double[] x = trace.getSolution();
        approx(x[1], 8.0, "Solução do traço: V(N2) = 8 V");
        double[][] last = trace.getSteps().get(trace.getSteps().size() - 1);
        for (int i = 0; i < last.length; i++) {
            for (int j = 0; j < i; j++) {
                check(Math.abs(last[i][j]) < 1e-9,
                    "Matriz final devia ser triangular superior (posição " + i + "," + j + ").");
            }
        }
        check(!trace.getBackSubstitution().isEmpty(), "Traço devia ter retrosubstituição.");
    }

    /** Erro: nó flutuante (resistor entre dois nós sem caminho até ao terra). */
    public static void erroNoFlutuante() {
        Circuit c = new Circuit("Flutuante");
        Node n1 = node(c, 100);
        Node n2 = node(c, 200);
        Node n3 = node(c, 300);
        c.addComponent(new VoltageSource("V1", "V1", n1.getId(), Node.GROUND_ID, 12, 100, 100, 100, 0));
        // n2 e n3 só se ligam entre si: sem caminho até ao terra.
        c.addComponent(new Resistor("R1", "R1", n2.getId(), n3.getId(), 1000, 200, 100, 300, 100));
        try {
            MnaSolver.solve(c);
        } catch (SolverException e) {
            check(e.getMessage().contains("flutuante"), "Mensagem deve mencionar flutuante, foi: " + e.getMessage());
            return;
        }
        throw new AssertionError("Devia lançar SolverException (nó flutuante).");
    }
}
