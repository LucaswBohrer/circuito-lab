package com.circuitolab;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runner de testes próprio (sem JUnit): imprime PASS/FAIL e sai com código != 0 se falhar. */
public class TestRunner {
    public static void main(String[] args) {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("divisor de tensão 12V 1k+2k => 8V, 4mA", () -> run(SolverTest::divisorTensao));
        tests.put("resistores em paralelo", () -> run(SolverTest::resistoresParalelo));
        tests.put("ponte de Wheatstone equilibrada (I central ~0)", () -> run(SolverTest::ponteWheatstoneEquilibrada));
        tests.put("fonte de corrente", () -> run(SolverTest::fonteCorrente));
        tests.put("duas fontes", () -> run(SolverTest::duasFontes));
        tests.put("capacitor em DC (circuito aberto)", () -> run(SolverTest::capacitorDcAberto));
        tests.put("indutor em DC (curto-circuito)", () -> run(SolverTest::indutorDcCurto));
        tests.put("resistores em série (I=2 mA, 10 V e 6 V)", () -> run(SolverTest::resistoresSerie));
        tests.put("delta-estrela (VA 9,333 V, VB 6,667 V, VC 4 V)", () -> run(SolverTest::deltaEstrela));
        tests.put("pruneUnusedNodes remove nós soltos", SolverTest::pruneUnusedNodes);
        tests.put("traço da eliminação de Gauss (divisor)", () -> run(SolverTest::traceEliminacaoGauss));
        tests.put("erro: circuito sem terra", SolverTest::erroSemTerra);
        tests.put("erro: fonte de tensão em curto", SolverTest::erroFonteEmCurto);
        tests.put("erro: circuito vazio", SolverTest::erroCircuitoVazio);
        tests.put("erro: nó flutuante", SolverTest::erroNoFlutuante);
        tests.put("ValueParser: 2k, 10m, 1,5, 10u, 2kΩ", SolverTest::valueParser);
        tests.put("renomear rótulo: isLabelUsed, id inalterado", () -> run(SolverTest::renomearRotulo));
        tests.put("renumerar componentes (R1, R2, V1; solução igual)", () -> run(SolverTest::renumberComponents));
        tests.put("renumerar nós sem mudar o resultado", () -> run(SolverTest::renumberNodes));
        tests.put("passo a passo explicado (secções 0-6)", () -> run(SolverTest::stepByStepExplained));
        tests.put("exportar PDF (válido, imagem, >1 página)", () -> run(SolverTest::pdfExport));
        tests.put("redes: N0/N4/N6/N8 são o mesmo ponto (circuito do Lucas)", SolverTest::netMergerLucasCircuit);
        tests.put("versão simples: 1 incógnita 2,773 V (circuito do Lucas)", () -> run(SolverTest::simpleStepByStepLucasCircuit));
        tests.put("versão completa continua igual (11 incógnitas, Gauss)", () -> run(SolverTest::completeVersionUnchanged));
        tests.put("versão simples com 2 incógnitas (ponte)", () -> run(SolverTest::simpleStepByStepMultiUnknown));
        tests.put("PDF: simples primeiro + apêndice completo", () -> run(SolverTest::pdfSimplePlusAppendix));

        int passed = 0;
        int failed = 0;
        for (Map.Entry<String, Runnable> e : tests.entrySet()) {
            try {
                e.getValue().run();
                System.out.println("PASS - " + e.getKey());
                passed++;
            } catch (Throwable t) {
                System.out.println("FAIL - " + e.getKey() + ": " + t.getMessage());
                failed++;
            }
        }
        System.out.println("----------------------------------------");
        System.out.println(passed + " passaram, " + failed + " falharam, de " + tests.size() + " testes.");
        if (failed > 0) System.exit(1);
    }

    /** Adaptador: métodos de teste que lançam checked exceptions. */
    private static void run(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
