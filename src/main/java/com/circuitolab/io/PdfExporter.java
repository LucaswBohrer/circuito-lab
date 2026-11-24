package com.circuitolab.io;

import com.circuitolab.model.Circuit;
import com.circuitolab.solver.Solution;
import com.circuitolab.solver.StepByStep;
import com.circuitolab.ui.CircuitCanvas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Exporta o passo a passo para PDF, com a imagem do desenho do circuito.
 *
 * PDF escrito à mão, em Java puro (sem nenhuma biblioteca externa):
 * objetos, xref, fontes base Helvetica / Helvetica-Bold (sem embedar
 * fonte), texto em WinAnsi (Latin-1) para os acentos, A4 com margens,
 * cabeçalho com o nome do circuito e rodapé com o número da página.
 * A imagem do circuito é renderizada do CircuitCanvas num BufferedImage
 * offscreen e embebida como imagem FlateDecode (RGB, sem PNG cru).
 */
public final class PdfExporter {
    private PdfExporter() {}

    // A4 em pontos.
    private static final double PAGE_W = 595;
    private static final double PAGE_H = 842;
    private static final double MARGIN = 48;
    private static final double TEXT_W = PAGE_W - 2 * MARGIN;

    /**
     * Gera o PDF do circuito resolvido: primeiro a versão simples do
     * passo a passo e, como apêndice, a versão completa (com matriz).
     */
    public static void export(Circuit circuit, StepByStep steps, Path path) throws IOException {
        BufferedImage image = renderCircuitImage(circuit, steps.getSolution());
        String text = steps.formatSimple()
            + "\nApêndice — Versão completa (com matriz)\n"
            + "A versão completa, com as equações, a matriz e a eliminação de\n"
            + "Gauss, está a seguir. É a mesma conta, explicada com todo o detalhe.\n\n"
            + steps.format();
        export(circuit, text, image, path);
    }

    /** Gera o PDF com o texto do passo a passo e a imagem já renderizada. */
    public static void export(Circuit circuit, String stepsText, BufferedImage image, Path path)
            throws IOException {
        List<Page> pages = paginate(circuit.getName(), stepsText, image);
        byte[] pdf = buildPdf(circuit.getName(), pages, image);
        Files.write(path, pdf);
    }

    // ------------------------------------------------------------------
    // Renderização do desenho (offscreen, funciona sem display).
    // ------------------------------------------------------------------
    /** Desenha o circuito num BufferedImage, enquadrando os componentes. */
    public static BufferedImage renderCircuitImage(Circuit circuit, Solution solution) {
        CircuitCanvas canvas = new CircuitCanvas(circuit);
        if (solution != null) canvas.setSolution(solution);
        return canvas.renderImage();
    }

    // ------------------------------------------------------------------
    // Paginação do texto.
    // ------------------------------------------------------------------
    private record Line(String text, double fontSize, boolean bold) {}
    private record Page(List<Line> lines, boolean first) {}

    private static List<Page> paginate(String circuitName, String stepsText, BufferedImage image) {
        List<Line> all = new ArrayList<>();
        for (String raw : stepsText.split("\n", -1)) {
            String line = sanitize(raw);
            boolean matrix = line.stripLeading().startsWith("[");
            double size = matrix ? 8.0 : 10.0;
            boolean header = line.matches("^[0-9]\\. .*")
                || line.startsWith("Apêndice");
            if (header) {
                all.add(new Line("", 10, false));
                all.add(new Line(line, 12, true));
            } else {
                for (String wrapped : wrap(line, size, TEXT_W)) {
                    all.add(new Line(wrapped, size, false));
                }
            }
        }

        List<Page> pages = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        double y = MARGIN + 46; // título + imagem na 1ª página ocupam ~esta altura
        double imageH = 0;
        if (image != null) {
            double scale = Math.min(TEXT_W / image.getWidth(), 240.0 / image.getHeight());
            scale = Math.min(scale, 1.0);
            imageH = image.getHeight() * scale;
            y += imageH + 22; // imagem + legenda + espaço
        }
        double limit = PAGE_H - MARGIN - 24; // reserva o rodapé
        boolean first = true;
        for (Line line : all) {
            double lh = line.fontSize() * 1.4;
            if (y + lh > limit) {
                pages.add(new Page(current, first));
                current = new ArrayList<>();
                first = false;
                y = MARGIN + 22; // cabeçalho das páginas seguintes
            }
            current.add(line);
            y += lh;
        }
        pages.add(new Page(current, first));
        return pages;
    }

    /** Parte o texto em linhas que cabem na largura, pela largura real da fonte. */
    private static List<String> wrap(String line, double fontSize, double maxWidth) {
        List<String> out = new ArrayList<>();
        if (line.isBlank()) { out.add(""); return out; }
        String[] words = line.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String trial = cur.isEmpty() ? word : cur + " " + word;
            if (textWidth(trial, fontSize) <= maxWidth) {
                cur = new StringBuilder(trial);
            } else {
                if (!cur.isEmpty()) out.add(cur.toString());
                // Palavra sozinha maior que a linha: parte-se por caracteres.
                while (textWidth(word, fontSize) > maxWidth) {
                    int cut = 1;
                    while (cut < word.length()
                            && textWidth(word.substring(0, cut + 1), fontSize) <= maxWidth) cut++;
                    out.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                cur = new StringBuilder(word);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static double textWidth(String s, double fontSize) {
        double w = 0;
        for (int i = 0; i < s.length(); i++) w += widthOf(s.charAt(i));
        return w * fontSize / 1000.0;
    }

    // ------------------------------------------------------------------
    // Construção do PDF.
    // ------------------------------------------------------------------
    private static byte[] buildPdf(String circuitName, List<Page> pages, BufferedImage image)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Long> offsets = new ArrayList<>();

        out.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));

        int nPages = pages.size();
        // Objetos: 1 Catálogo, 2 Páginas, 3 Helvetica, 4 Helvetica-Bold,
        // 5 Imagem, depois por página: objeto Página e objeto Conteúdo.
        int imageObj = image != null ? 5 : -1;
        int firstPageObj = 6;
        List<Integer> pageObjs = new ArrayList<>();
        for (int i = 0; i < nPages; i++) pageObjs.add(firstPageObj + 2 * i);

        writeObj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder("<< /Type /Pages /Kids [");
        for (int p : pageObjs) kids.append(p).append(" 0 R ");
        kids.append("] /Count ").append(nPages).append(" >>");
        writeObj(out, offsets, 2, kids.toString());
        writeObj(out, offsets, 3,
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        writeObj(out, offsets, 4,
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        if (image != null) {
            byte[] compressed = compressImage(image);
            String dict = "<< /Type /XObject /Subtype /Image /Width " + image.getWidth()
                + " /Height " + image.getHeight()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + compressed.length + " >>";
            writeStreamObj(out, offsets, imageObj, dict, compressed);
        }
        for (int i = 0; i < nPages; i++) {
            int pageObj = pageObjs.get(i);
            int contentObj = pageObj + 1;
            String resources = "<< /Font << /F1 3 0 R /F2 4 0 R >>"
                + (image != null ? " /XObject << /Im1 5 0 R >>" : "") + " >>";
            writeObj(out, offsets, pageObj,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PAGE_W + " " + (int) PAGE_H
                + "] /Resources " + resources + " /Contents " + contentObj + " 0 R >>");
            byte[] content = pageContent(circuitName, pages.get(i), i + 1, nPages, image);
            writeStreamObj(out, offsets, contentObj, "<< /Length " + content.length + " >>", content);
        }

        long xrefPos = out.size();
        int total = firstPageObj + 2 * nPages - 1; // nº do último objeto escrito
        StringBuilder xref = new StringBuilder("xref\n0 " + (total + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= total; i++) {
            long off = i - 1 < offsets.size() ? offsets.get(i - 1) : 0;
            xref.append(String.format("%010d 00000 n \n", off));
        }
        xref.append("trailer\n<< /Size ").append(total + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xrefPos).append("\n%%EOF\n");
        out.write(xref.toString().getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static void writeObj(ByteArrayOutputStream out, List<Long> offsets, int num, String body)
            throws IOException {
        while (offsets.size() < num) offsets.add(0L);
        offsets.set(num - 1, (long) out.size());
        out.write((num + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body.getBytes(StandardCharsets.ISO_8859_1));
        out.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeStreamObj(ByteArrayOutputStream out, List<Long> offsets, int num,
                                       String dict, byte[] stream) throws IOException {
        while (offsets.size() < num) offsets.add(0L);
        offsets.set(num - 1, (long) out.size());
        out.write((num + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
        out.write(dict.getBytes(StandardCharsets.ISO_8859_1));
        out.write("\nstream\n".getBytes(StandardCharsets.US_ASCII));
        out.write(stream);
        out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
    }

    /** Conteúdo de uma página: cabeçalho, título+imagem (1ª pág.), linhas, rodapé. */
    private static byte[] pageContent(String circuitName, Page page, int pageNo, int nPages,
                                      BufferedImage image) throws IOException {
        ByteArrayOutputStream c = new ByteArrayOutputStream();
        // "top" = distância ao topo da página (a origem do PDF é em baixo).
        double top = MARGIN;
        if (page.first()) {
            drawText(c, "CircuitoLab — Passo a Passo", 16, true, MARGIN, PAGE_H - top - 16);
            top += 22;
            drawText(c, "Circuito: " + sanitize(circuitName), 11, false, MARGIN, PAGE_H - top - 11);
            top += 20;
            if (image != null) {
                double scale = Math.min(TEXT_W / image.getWidth(), 240.0 / image.getHeight());
                scale = Math.min(scale, 1.0);
                double w = image.getWidth() * scale;
                double h = image.getHeight() * scale;
                double imgBottom = PAGE_H - top - h;
                c.write(("q " + num(w) + " 0 0 " + num(h) + " " + num(MARGIN) + " "
                    + num(imgBottom) + " cm /Im1 Do Q\n").getBytes(StandardCharsets.US_ASCII));
                top += h + 6;
                drawText(c, "Desenho do circuito (como desenhado na GUI).", 9, false,
                    MARGIN, PAGE_H - top - 9);
                top += 16;
            }
        } else {
            drawText(c, "CircuitoLab — Passo a Passo — " + sanitize(circuitName), 9, false,
                MARGIN, PAGE_H - top - 9);
            top += 22;
        }
        for (Line line : page.lines()) {
            double lh = line.fontSize() * 1.4;
            drawText(c, line.text(), line.fontSize(), line.bold(), MARGIN, PAGE_H - top - line.fontSize());
            top += lh;
        }
        drawText(c, "Página " + pageNo + " de " + nPages, 9, false,
            PAGE_W / 2 - 20, MARGIN - 14);
        return c.toByteArray();
    }

    private static void drawText(ByteArrayOutputStream c, String text, double fontSize,
                                 boolean bold, double x, double baselineY) throws IOException {
        String font = bold ? "/F2" : "/F1";
        c.write(("BT " + font + " " + num(fontSize) + " Tf 1 0 0 1 " + num(x) + " "
            + num(baselineY) + " Tm (").getBytes(StandardCharsets.US_ASCII));
        c.write(escapeWinAnsi(text));
        c.write(") Tj ET\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static byte[] compressImage(BufferedImage image) throws IOException {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] rgb = new byte[w * h * 3];
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            rgb[3 * i] = (byte) ((pixels[i] >> 16) & 0xFF);
            rgb[3 * i + 1] = (byte) ((pixels[i] >> 8) & 0xFF);
            rgb[3 * i + 2] = (byte) (pixels[i] & 0xFF);
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(rgb);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // WinAnsi: a fonte Helvetica só cobre o Latin-1/WinAnsi — os
    // caracteres fora dele são substituídos antes de medir e escrever.
    // ------------------------------------------------------------------
    /** Substitui caracteres sem equivalente WinAnsi (Ω, setas, etc.). */
    static String sanitize(String s) {
        return s
            .replace("Ω", "ohm").replace("→", "->").replace("−", "-")
            .replace("≈", "~").replace("≥", ">=").replace("≤", "<=")
            .replace("Σ", "Soma").replace("Δ", "Delta").replace("✓", "OK")
            .replace("↔", "<->").replace("·", ".").replace("²", "^2")
            .replace("\r", "");
    }

    /** Escapa e converte o texto para bytes WinAnsi (literal string do PDF). */
    private static byte[] escapeWinAnsi(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == ')' || ch == '\\') out.write('\\');
            out.write(toWinAnsiByte(ch));
        }
        return out.toByteArray();
    }

    /** Byte WinAnsi de um caráter (mapeia a pontuação fora do Latin-1). */
    private static int toWinAnsiByte(char ch) {
        return switch (ch) {
            case '€' -> 0x80;
            case '‚' -> 0x82;
            case 'ƒ' -> 0x83;
            case '„' -> 0x84;
            case '…' -> 0x85;
            case '†' -> 0x86;
            case '‡' -> 0x87;
            case 'ˆ' -> 0x88;
            case '‰' -> 0x89;
            case 'Š' -> 0x8A;
            case '‹' -> 0x8B;
            case 'Œ' -> 0x8C;
            case 'Ž' -> 0x8E;
            case '‘' -> 0x91;
            case '’' -> 0x92;
            case '“' -> 0x93;
            case '”' -> 0x94;
            case '•' -> 0x95;
            case '–' -> 0x96;
            case '—' -> 0x97;
            case '˜' -> 0x98;
            case '™' -> 0x99;
            case 'š' -> 0x9A;
            case '›' -> 0x9B;
            case 'œ' -> 0x9C;
            case 'ž' -> 0x9E;
            case 'Ÿ' -> 0x9F;
            default -> ch <= 0xFF ? ch : '?';
        };
    }

    /** Largura de um caráter Helvetica (unidades de 1/1000 em). */
    private static int widthOf(char ch) {
        return switch (ch) {
            case ' ' -> 278;
            case '!', 'i', 'j', 'l', '.', ',', ':', ';', '\'', '¡' -> 222;
            case '"', '”', '“' -> 355;
            case '#', '$', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                 'a', 'b', 'd', 'e', 'g', 'h', 'n', 'o', 'p', 'q', 'u', 'à', 'á', 'â', 'ã', 'ä', 'å',
                 'è', 'é', 'ê', 'ë', 'ñ', 'ò', 'ó', 'ô', 'õ', 'ö', 'ù', 'ú', 'û', 'ü', 'æ', 'ß', 'ø', 'µ' -> 556;
            case '%', 'W', 'œ' -> 889;
            case '&', 'A', 'B', 'E', 'K', 'R', 'S', 'V', 'X', 'Y', 'À', 'Á', 'Â', 'Ã', 'Ä', 'Å', 'Ý' -> 667;
            case '(', ')', '*', '-', '/', '[', ']', '\\', '^', '–', 'r' -> 333;
            case '+', '<', '=', '>', '~', '±', '×', '÷' -> 584;
            case 'C', 'G', 'H', 'N', 'Ç', 'Ñ', 'Ò', 'Ó', 'Ô', 'Õ', 'Ö', 'Ù', 'Ú', 'Û', 'Ü', 'w' -> 722;
            case 'D', 'O', 'Q', 'Ø' -> 778;
            case 'F', 'T', 'Z', 'È', 'É', 'Ê', 'Ë', 'f', 't', '·' -> 611;
            case 'I', 'J' -> 278;
            case 'L', 'c', 'k', 's', 'v', 'x', 'y', 'z', 'ç', 'ý', 'ÿ' -> 500;
            case 'M', 'm' -> 833;
            case 'P', 'U' -> 722;
            case '@' -> 1015;
            case '?' -> 556;
            case '_', '—', 'Æ', 'Œ', '…' -> 1000;
            case '{', '}' -> 334;
            case '|' -> 260;
            case '©', '®' -> 737;
            case '°' -> 400;
            case '½', '¼', '¾' -> 834;
            case '€', '™' -> 1000;
            default -> 556;
        };
    }
}
