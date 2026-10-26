package com.neram.pos.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Turns the shop's payment QR picture (neram-qr.png: a QR code with its UPI id underneath) into a thermal-printer image that
 * a phone can scan. A plain "shrink the picture" would blur the modules, so the code is read back into its grid of modules and
 * redrawn with a whole number of printer dots per module (sharp edges), with the required white margin around it; the caption
 * is scaled up so it stays readable.
 */
final class PaymentQrRaster {

    private PaymentQrRaster() {}

    /** The QR as a black-and-white module grid, plus the caption (UPI id) region of the picture. */
    static final class Parsed {
        final boolean[][] modules;
        final BufferedImage caption;      // may be null
        Parsed(boolean[][] modules, BufferedImage caption) { this.modules = modules; this.caption = caption; }
        int size() { return modules.length; }
    }

    private static int gray(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        return (lum * a + 255 * (255 - a)) / 255;           // transparent pixels count as white paper
    }

    /** Reads the module grid and the caption out of the picture; null when the picture is not a QR code we can read. */
    static Parsed parse(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        boolean[][] dark = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) dark[y][x] = gray(src.getRGB(x, y)) < 128;

        // Row blocks of dark pixels, separated by clear gaps (the QR first, the caption lines after it)
        final int GAP = Math.max(6, h / 40);
        int[] qrRows = firstBlock(rowHasDark(dark, w, h), GAP, 0);
        if (qrRows == null) return null;
        int top = qrRows[0], bottom = qrRows[1];
        int left = w, right = -1;
        for (int y = top; y <= bottom; y++) for (int x = 0; x < w; x++) if (dark[y][x]) { left = Math.min(left, x); right = Math.max(right, x); }
        int width = right - left + 1, height = bottom - top + 1;
        if (width < 21 || Math.abs(width - height) > Math.max(3, width / 25)) return null;      // not square: not a QR code

        // The top-left finder pattern is 7 modules wide, followed by a white separator: that gives the module size
        int run = 0;
        for (int x = left; x <= right && dark[top][x]; x++) run++;
        double module = run / 7.0;
        if (module < 1) return null;
        int n = (int) Math.round(width / module);
        if (n < 21 || n > 177 || (n - 17) % 4 != 0) return null;                              // QR sizes are 21, 25, 29 ... modules

        double step = width / (double) n;
        boolean[][] grid = new boolean[n][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int cx = left + (int) Math.floor((i + 0.5) * step);
                int cy = top + (int) Math.floor((j + 0.5) * step);
                grid[j][i] = dark[Math.min(h - 1, cy)][Math.min(w - 1, cx)];
            }
        }
        // sanity: all three finder patterns must have their dark corner
        if (!(grid[0][0] && grid[0][n - 1] && grid[n - 1][0])) return null;

        // Caption: everything dark below the QR
        int capTop = h, capBottom = -1, capLeft = w, capRight = -1;
        for (int y = bottom + GAP; y < h; y++) for (int x = 0; x < w; x++) if (dark[y][x]) {
            capTop = Math.min(capTop, y); capBottom = Math.max(capBottom, y); capLeft = Math.min(capLeft, x); capRight = Math.max(capRight, x);
        }
        BufferedImage caption = null;
        if (capBottom >= capTop) {
            int pad = 2;
            int cx = Math.max(0, capLeft - pad), cy = Math.max(0, capTop - pad);
            int cw = Math.min(w - cx, capRight - capLeft + 1 + 2 * pad), ch = Math.min(h - cy, capBottom - capTop + 1 + 2 * pad);
            caption = src.getSubimage(cx, cy, cw, ch);
        }
        return new Parsed(grid, caption);
    }

    private static boolean[] rowHasDark(boolean[][] dark, int w, int h) {
        boolean[] rows = new boolean[h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (dark[y][x]) { rows[y] = true; break; }
        return rows;
    }

    /** First run of rows that have dark pixels, merging gaps shorter than {@code gap}; {top, bottom} or null. */
    private static int[] firstBlock(boolean[] rows, int gap, int from) {
        int start = -1, last = -1;
        for (int y = from; y < rows.length; y++) {
            if (rows[y]) {
                if (start < 0) start = y;
                last = y;
            } else if (start >= 0 && y - last >= gap) break;
        }
        return start < 0 ? null : new int[]{start, last};
    }

    /** Share of the largest comfortable size that is actually printed (60%: about 25 mm wide on 80 mm paper, still easy to scan). */
    static final double PRINT_SIZE = 0.6;

    /** Dots per module for a paper width: the largest size that fits (at most 12 dots), shrunk to {@link #PRINT_SIZE}, never below 6 dots (0.75 mm). */
    static int dotsPerModule(int paperDots, int modules) {
        double fit = Math.min(12.0, paperDots * 0.7 / modules);
        return Math.max(6, (int) Math.floor(fit * PRINT_SIZE));
    }

    /** The picture to print (black and white only): quiet zone, QR at an exact dot size, and the caption underneath. */
    static BufferedImage compose(Parsed p, int paperDots) {
        int n = p.size();
        int m = dotsPerModule(paperDots, n);
        int quiet = 4 * m;                                    // the QR standard asks for 4 white modules around the code
        int qrDots = n * m;
        int width = qrDots + 2 * quiet;

        BufferedImage cap = null;
        if (p.caption != null) {
            double scale = Math.min(2.0, (width - 8) / (double) p.caption.getWidth());
            if (scale >= 1.0) {
                int cw = (int) Math.round(p.caption.getWidth() * scale), ch = (int) Math.round(p.caption.getHeight() * scale);
                BufferedImage g = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = g.createGraphics();
                g2.setColor(Color.WHITE); g2.fillRect(0, 0, cw, ch);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(p.caption, 0, 0, cw, ch, null);
                g2.dispose();
                cap = g;
            }
        }
        int height = quiet + qrDots + (cap != null ? quiet / 2 + cap.getHeight() + 6 : quiet);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) if (p.modules[j][i]) g.fillRect(quiet + i * m, quiet + j * m, m, m);
        g.dispose();

        if (cap != null) {
            int ox = (width - cap.getWidth()) / 2, oy = quiet + qrDots + quiet / 2;
            for (int y = 0; y < cap.getHeight(); y++) for (int x = 0; x < cap.getWidth(); x++) {
                int rgb = cap.getRGB(x, y);
                int lum = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                if (lum < 150) out.setRGB(ox + x, oy + y, 0xFF000000);        // text: crisp black dots, no grey
            }
        }
        return out;
    }

    /** ESC/POS "GS v 0" raster command for a black-and-white picture, centred on paper {@code paperDots} wide. */
    static byte[] toEscPos(BufferedImage img, int paperDots) {
        int w = img.getWidth(), h = img.getHeight();
        int paperBytes = paperDots / 8;
        int imgBytes = (w + 7) / 8;
        int leftPad = Math.max(0, (paperBytes - imgBytes) / 2);
        int rightPad = Math.max(0, paperBytes - imgBytes - leftPad);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0x00);
        out.write(paperBytes % 256); out.write(paperBytes / 256);
        out.write(h % 256); out.write(h / 256);
        for (int y = 0; y < h; y++) {
            for (int i = 0; i < leftPad; i++) out.write(0);
            int cur = 0, bits = 0;
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int lum = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                cur = (cur << 1) | (lum < 128 ? 1 : 0);
                if (++bits == 8) { out.write(cur); cur = 0; bits = 0; }
            }
            if (bits > 0) out.write(cur << (8 - bits));
            for (int i = 0; i < rightPad; i++) out.write(0);
        }
        return out.toByteArray();
    }

    /** Everything in one go; null when the picture cannot be read as a QR code (the bill then simply prints without it). */
    static byte[] build(BufferedImage src, int paperDots) {
        Parsed p = parse(src);
        return p == null ? null : toEscPos(compose(p, paperDots), paperDots);
    }
}
