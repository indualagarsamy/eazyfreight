package com.eazyfreight.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A small amount of layout on top of PDFBox, so a document renderer can say what it
 * wants on the page rather than where every glyph goes.
 *
 * <p>PDFBox works in absolute coordinates with the origin at the bottom-left and no
 * concept of flow. That is the right primitive for a PDF library and the wrong one for
 * writing a Bill of Lading, where what you actually want to say is "a heading, then a
 * two-column block, then a table". This keeps a cursor, moves it down as things are
 * written, and breaks the page when it runs out of room.
 *
 * <p>Only the Standard 14 fonts are used, so no font file has to be shipped, embedded
 * or licensed. That limits the character set to WinAnsi — {@link #sanitise} replaces
 * what will not encode rather than letting an em dash in a customer's address throw
 * halfway through rendering a document.
 */
public final class PdfCanvas implements AutoCloseable {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 48f;
    private static final float BOTTOM_MARGIN = 56f;

    public static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    public static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    public static final PDFont MONO = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private final PDDocument document = new PDDocument();
    private final List<Runnable> footers = new ArrayList<>();
    private PDPageContentStream stream;
    private float cursor;

    public PdfCanvas() {
        newPage();
    }

    public float contentWidth() {
        return PAGE_SIZE.getWidth() - (2 * MARGIN);
    }

    public float left() {
        return MARGIN;
    }

    public float cursorY() {
        return cursor;
    }

    /** Moves the cursor down without drawing anything. */
    public PdfCanvas gap(float points) {
        cursor -= points;
        return this;
    }

    public PdfCanvas text(String value, float x, float size, PDFont font) {
        write(sanitise(value), x, cursor, size, font);
        return this;
    }

    /** Writes a line and advances the cursor past it. */
    public PdfCanvas line(String value, float size, PDFont font) {
        breakIfNeeded(size + 4);
        write(sanitise(value), MARGIN, cursor, size, font);
        cursor -= size + 4;
        return this;
    }

    /** A right-aligned line at the given right edge, without moving the cursor. */
    public PdfCanvas rightText(String value, float rightEdge, float size, PDFont font) {
        String safe = sanitise(value);
        write(safe, rightEdge - width(safe, font, size), cursor, size, font);
        return this;
    }

    /** A section heading: small, spaced capitals over a rule. */
    public PdfCanvas heading(String label) {
        breakIfNeeded(26);
        cursor -= 6;
        write(spaced(sanitise(label.toUpperCase())), MARGIN, cursor, 7.5f, BOLD);
        cursor -= 5;
        rule();
        cursor -= 9;
        return this;
    }

    /** A label above a value, occupying one column of a row. */
    public PdfCanvas field(String label, String value, float x, float columnWidth) {
        write(spaced(sanitise(label.toUpperCase())), x, cursor, 6.5f, REGULAR);
        List<String> lines = wrap(sanitise(blankToDash(value)), REGULAR, 9f, columnWidth);
        float y = cursor - 11;
        for (String part : lines) {
            write(part, x, y, 9f, REGULAR);
            y -= 11;
        }
        return this;
    }

    /**
     * Lays out fields across equal columns and drops the cursor past the tallest of
     * them, so a long consignee address does not overwrite the row beneath it.
     */
    public PdfCanvas fieldRow(String... labelsAndValues) {
        int columns = labelsAndValues.length / 2;
        float columnWidth = (contentWidth() - (columns - 1) * 12f) / columns;
        int tallest = 1;
        for (int i = 0; i < columns; i++) {
            String value = blankToDash(labelsAndValues[(i * 2) + 1]);
            tallest = Math.max(tallest, wrap(sanitise(value), REGULAR, 9f, columnWidth).size());
        }
        breakIfNeeded(14 + (tallest * 11));
        for (int i = 0; i < columns; i++) {
            field(labelsAndValues[i * 2], labelsAndValues[(i * 2) + 1],
                    MARGIN + (i * (columnWidth + 12f)), columnWidth);
        }
        cursor -= 13 + (tallest * 11);
        return this;
    }

    /** A horizontal rule across the content width. */
    public PdfCanvas rule() {
        try {
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, cursor);
            stream.lineTo(PAGE_SIZE.getWidth() - MARGIN, cursor);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * One table row. Negative widths right-align the cell, which is what money and
     * quantities want and what labels do not.
     */
    public PdfCanvas row(float size, PDFont font, String[] cells, float[] widths) {
        breakIfNeeded(size + 8);
        float x = MARGIN;
        for (int i = 0; i < cells.length; i++) {
            float cellWidth = Math.abs(widths[i]);
            // An empty table cell stays empty. The dash is for a field whose value is
            // unknown; in a totals row it reads as "quantity: none" rather than "not
            // applicable here".
            String value = sanitise(cells[i] == null ? "" : cells[i]);
            if (widths[i] < 0) {
                write(value, x + cellWidth - width(value, font, size), cursor, size, font);
            } else {
                write(truncate(value, font, size, cellWidth - 6), x, cursor, size, font);
            }
            x += cellWidth;
        }
        cursor -= size + 6;
        return this;
    }

    /** A paragraph wrapped to the content width. */
    public PdfCanvas paragraph(String value, float size, PDFont font) {
        for (String part : wrap(sanitise(value), font, size, contentWidth())) {
            breakIfNeeded(size + 3);
            write(part, MARGIN, cursor, size, font);
            cursor -= size + 3;
        }
        return this;
    }

    /**
     * Text stamped at the foot of every page, present and future.
     *
     * <p>Registered rather than drawn once because the provenance notice has to appear
     * on page three as well as page one — a document that says what it is only on its
     * first page says nothing at all once someone prints a single sheet of it.
     */
    public PdfCanvas footerOnEveryPage(String value) {
        String safe = sanitise(value);
        footers.add(() -> write(safe, MARGIN, 34f, 7f, REGULAR));
        drawFooters();
        return this;
    }

    public byte[] toByteArray() {
        try {
            stream.close();
            stream = null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (stream != null) {
                stream.close();
            }
            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ----------------------------------------------------------------- internals

    private void breakIfNeeded(float required) {
        if (cursor - required < BOTTOM_MARGIN) {
            newPage();
        }
    }

    private void newPage() {
        try {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursor = PAGE_SIZE.getHeight() - MARGIN;
            drawFooters();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drawFooters() {
        footers.forEach(Runnable::run);
    }

    private void write(String value, float x, float y, float size, PDFont font) {
        try {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(value);
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float width(String value, PDFont font, float size) {
        try {
            return font.getStringWidth(value) / 1000 * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> wrap(String value, PDFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate, font, size) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private static String truncate(String value, PDFont font, float size, float maxWidth) {
        if (width(value, font, size) <= maxWidth) {
            return value;
        }
        String trimmed = value;
        while (trimmed.length() > 1 && width(trimmed + "...", font, size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    /** Letter-spaced small capitals, for labels. */
    private static String spaced(String value) {
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            spaced.append(value.charAt(i));
            if (i < value.length() - 1) {
                spaced.append(' ');
            }
        }
        return spaced.toString();
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Replaces what the Standard 14 fonts cannot encode.
     *
     * <p>The alternative is an exception thrown mid-render on a document that is
     * otherwise fine, triggered by a character nobody typed deliberately — a curly
     * apostrophe pasted out of a customer's email, or the em dash this codebase uses
     * throughout its own prose.
     */
    static String sanitise(String value) {
        if (value == null) {
            return "-";
        }
        String replaced = value
                .replace('—', '-').replace('–', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('…', '.')
                .replace(' ', ' ');
        StringBuilder safe = new StringBuilder(replaced.length());
        for (char c : replaced.toCharArray()) {
            safe.append(c < 32 ? ' ' : c > 255 ? '?' : c);
        }
        return safe.toString();
    }
}
