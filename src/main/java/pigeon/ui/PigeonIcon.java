package pigeon.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public final class PigeonIcon {
    private static final int SIZE = 16;

    private PigeonIcon() {
    }

    public static BufferedImage create() {
        return create(SIZE);
    }

    public static BufferedImage create(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Icon size must be positive");
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.scale(size / (double) SIZE, size / (double) SIZE);
            graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            Path2D body = new Path2D.Double();
            body.moveTo(2.0, 10.5);
            body.curveTo(3.4, 7.0, 5.4, 5.0, 8.1, 4.8);
            body.curveTo(9.4, 4.7, 10.7, 5.2, 11.5, 6.2);
            body.lineTo(14.0, 6.9);
            body.lineTo(11.8, 8.0);
            body.curveTo(11.2, 10.5, 9.4, 12.1, 6.7, 12.1);
            body.lineTo(3.1, 12.1);
            body.closePath();

            graphics.setColor(new Color(126, 168, 194));
            graphics.fill(body);
            graphics.setColor(new Color(220, 231, 238));
            graphics.draw(body);

            graphics.setColor(new Color(236, 169, 76));
            graphics.drawLine(5, 12, 4, 14);
            graphics.drawLine(8, 12, 9, 14);

            graphics.setColor(Color.WHITE);
            graphics.fillOval(9, 6, 2, 2);
            graphics.setColor(new Color(30, 30, 30));
            graphics.fillOval(10, 6, 1, 1);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
