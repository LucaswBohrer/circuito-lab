package com.circuitolab;

import com.circuitolab.model.Capacitor;
import com.circuitolab.model.Circuit;
import com.circuitolab.model.CurrentSource;
import com.circuitolab.model.Inductor;
import com.circuitolab.model.Node;
import com.circuitolab.model.Resistor;
import com.circuitolab.model.VoltageSource;
import com.circuitolab.solver.MnaSolver;
import com.circuitolab.solver.Solution;
import com.circuitolab.solver.SolverException;

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
