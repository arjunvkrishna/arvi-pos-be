package com.neram.pos.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/** A synthetic version-3 style QR picture (29 x 29 modules, finder patterns, caption) for printer tests. */
final class QrFixture {

    static final int MODULES = 29;
    static final int PX = 10;
    static final int QUIET = 4;

    private QrFixture() {
    }

    static BufferedImage image() {
        int side = (MODULES + 2 * QUIET) * PX;
        BufferedImage img = new BufferedImage(side, side + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        boolean[][] m = new boolean[MODULES][MODULES];
        Random random = new Random(42);
        for (int y = 0; y < MODULES; y++) for (int x = 0; x < MODULES; x++) m[y][x] = random.nextBoolean();
        finder(m, 0, 0);
        finder(m, MODULES - 7, 0);
        finder(m, 0, MODULES - 7);
        for (int i = 8; i < MODULES - 8; i++) { m[6][i] = i % 2 == 0; m[i][6] = i % 2 == 0; }
        g.setColor(Color.BLACK);
        for (int y = 0; y < MODULES; y++) for (int x = 0; x < MODULES; x++) {
            if (m[y][x]) g.fillRect((QUIET + x) * PX, (QUIET + y) * PX, PX, PX);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString("merchant@upi", QUIET * PX + 20, side + 30);
        g.dispose();
        return img;
    }

    private static void finder(boolean[][] m, int x0, int y0) {
        for (int y = -1; y <= 7; y++) for (int x = -1; x <= 7; x++) {
            int xx = x0 + x, yy = y0 + y;
            if (xx < 0 || yy < 0 || xx >= MODULES || yy >= MODULES) continue;
            boolean in = x >= 0 && x <= 6 && y >= 0 && y <= 6;
            boolean ring = in && (x == 0 || x == 6 || y == 0 || y == 6);
            boolean centre = in && x >= 2 && x <= 4 && y >= 2 && y <= 4;
            m[yy][xx] = ring || centre;
        }
    }

    static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image(), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
