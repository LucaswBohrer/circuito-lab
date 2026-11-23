package com.circuitolab.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser JSON mínimo (objetos, arrays, strings, números, true/false/null),
 * suficiente para o formato próprio do CircuitoLab. Sem dependências.
 */
public final class JsonParser {
    private final String src;
    private int pos;

    private JsonParser(String src) { this.src = src; }

    public static Object parse(String src) {
        JsonParser p = new JsonParser(src);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.src.length()) throw new IllegalArgumentException("Conteúdo extra após o JSON.");
        return v;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw new IllegalArgumentException("Fim inesperado do JSON.");
        return src.charAt(pos);
    }

    private Object value() {
        skipWs();
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') { expect("true"); return Boolean.TRUE; }
        if (c == 'f') { expect("false"); return Boolean.FALSE; }
        if (c == 'n') { expect("null"); return null; }
        return number();
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) throw new IllegalArgumentException("Token inválido em " + pos);
        pos += word.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            if (peek() != ':') throw new IllegalArgumentException("Esperado ':' em " + pos);
            pos++;
            map.put(key, value());
            skipWs();
            char c = peek();
            if (c == '}') { pos++; return map; }
            if (c != ',') throw new IllegalArgumentException("Esperado ',' ou '}' em " + pos);
            pos++;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(value());
            skipWs();
            char c = peek();
            if (c == ']') { pos++; return list; }
            if (c != ',') throw new IllegalArgumentException("Esperado ',' ou ']' em " + pos);
            pos++;
        }
    }

    private String string() {
        if (peek() != '"') throw new IllegalArgumentException("Esperado '\"' em " + pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) break;
                char e = src.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw new IllegalArgumentException("Escape \\u inválido.");
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Escape inválido: \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("String sem fecho.");
    }

    private Number number() {
        int start = pos;
        while (pos < src.length() && "-+0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
        if (start == pos) throw new IllegalArgumentException("Número inválido em " + pos);
        return Double.parseDouble(src.substring(start, pos));
    }

    /** Escapa uma string para serialização JSON. */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
