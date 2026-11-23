package com.circuitolab.solver;

/** Erro de validação ou de resolução do circuito, com mensagem em PT-BR. */
public class SolverException extends Exception {
    public SolverException(String message) {
        super(message);
    }
}
