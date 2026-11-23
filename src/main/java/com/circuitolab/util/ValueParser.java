package com.circuitolab.util;

import java.util.Locale;

/**
 * Interpretação e formatação amigável de valores de componentes.
 *
 * parse() aceita o que o aluno digita no diálogo de edição:
 * vírgula como separador decimal ("1,5") e sufixos de engenharia
 * ("2k" = 2000, "10m" = 0,01, "10u" = 0,00001, "2,2k" = 2200).
 * Também aceita um símbolo de unidade no fim ("2kΩ", "10mA").
 *
 * Os métodos fmt*() formatam valores com unidades adequadas e 3 casas
 * decimais, em PT-BR (vírgula decimal), sem nunca usar notação
 * científica — são os números que aparecem nas explicações do passo
 * a passo.
 */
public final class ValueParser {
    private ValueParser() {}

    /** Converte texto digitado (ex.: "2k", "1,5", "10m") num valor double. */
    public static double parse(String text) {
        if (text == null) throw new NumberFormatException("Valor vazio.");
        String s = text.trim().replace(',', '.').replaceAll("\\s+", "");
        if (s.isEmpty()) throw new NumberFormatException("Valor vazio.");
        // Símbolo de unidade no fim ("2kΩ", "10mA", "12V"): tira-o.
        if (s.length() > 1 && "ΩVAFH".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        double mult = 1.0;
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            switch (last) {
                case 'k', 'K' -> { mult = 1e3; s = s.substring(0, s.length() - 1); }
                case 'M' -> { mult = 1e6; s = s.substring(0, s.length() - 1); }
                case 'm' -> { mult = 1e-3; s = s.substring(0, s.length() - 1); }
                case 'u', 'µ' -> { mult = 1e-6; s = s.substring(0, s.length() - 1); }
                case 'n' -> { mult = 1e-9; s = s.substring(0, s.length() - 1); }
                case 'p' -> { mult = 1e-12; s = s.substring(0, s.length() - 1); }
                default -> { /* sem sufixo */ }
            }
        }
        double v;
        try {
            v = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Valor inválido: \"" + text + "\". "
                + "Exemplos válidos: 20, 2k, 1,5, 10m, 10u.");
        }
        if (!Double.isFinite(v)) throw new NumberFormatException("Valor inválido: \"" + text + "\".");
        return v * mult;
    }

    /** Número com 3 casas decimais e vírgula decimal (ex.: "2,437"). */
    public static String fmt3(double v) {
        if (v == 0.0) return "0,000";
        return String.format(Locale.US, "%.3f", v).replace('.', ',');
    }

    /** Resistência amigável: "20,000 Ω", "1,000 kΩ", "2,200 MΩ". */
    public static String fmtResistance(double ohms) {
        double a = Math.abs(ohms);
        if (a >= 1e6) return fmt3(ohms / 1e6) + " MΩ";
        if (a >= 1000) return fmt3(ohms / 1000) + " kΩ";
        if (a >= 1) return fmt3(ohms) + " Ω";
        return fmt3(ohms * 1000) + " mΩ";
    }

    /** Tensão amigável: "12,000 V", "0,500 V". */
    public static String fmtVoltage(double v) {
        return fmt3(v) + " V";
    }

    /** Corrente amigável: "0,378 A", "4,000 mA", "250,000 µA". */
    public static String fmtCurrent(double a) {
        if (a == 0.0) return "0,000 A";
        double m = Math.abs(a);
        if (m >= 1) return fmt3(a) + " A";
        if (m >= 0.001) return fmt3(a * 1000) + " mA";
        if (m >= 0.000001) return fmt3(a * 1e6) + " µA";
        return fmt3(a * 1e9) + " nA";
    }

    /** Potência amigável: "2,860 W", "16,000 mW". */
    public static String fmtPower(double w) {
        if (w == 0.0) return "0,000 W";
        double m = Math.abs(w);
        if (m >= 1) return fmt3(w) + " W";
        if (m >= 0.001) return fmt3(w * 1000) + " mW";
        return fmt3(w * 1e6) + " µW";
    }

    /** Condutância amigável: "0,050 S", "50,000 mS". */
    public static String fmtConductance(double siemens) {
        if (siemens == 0.0) return "0,000 S";
        double m = Math.abs(siemens);
        if (m >= 1) return fmt3(siemens) + " S";
        if (m >= 0.001) return fmt3(siemens * 1000) + " mS";
        return fmt3(siemens * 1e6) + " µS";
    }

    /** Capacitância amigável: "1,000 µF", "100,000 nF". */
    public static String fmtCapacitance(double farads) {
        double m = Math.abs(farads);
        if (m >= 0.001) return fmt3(farads * 1000) + " mF";
        if (m >= 0.000001) return fmt3(farads * 1e6) + " µF";
        return fmt3(farads * 1e9) + " nF";
    }

    /** Indutância amigável: "1,000 mH", "2,500 H". */
    public static String fmtInductance(double henries) {
        double m = Math.abs(henries);
        if (m >= 1) return fmt3(henries) + " H";
        return fmt3(henries * 1000) + " mH";
    }
}
