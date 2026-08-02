package pigeon.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class PigeonActionIcon implements Icon {
    enum Type {
        ADD,
        IMPORT,
        EXPORT,
        CONFIGURE,
        CLONE,
        DELETE
    }

    private static final int SIZE = 18;
    private final Type type;
    private final Color color;

    PigeonActionIcon(Type type, Color color) {
        this.type = type;
        this.color = color;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (type) {
                case ADD:
                    drawAdd(g);
                    break;
                case IMPORT:
                    drawClipboard(g, true);
                    break;
                case EXPORT:
                    drawClipboard(g, false);
                    break;
                case CONFIGURE:
                    drawPencil(g);
                    break;
                case CLONE:
                    drawClone(g);
                    break;
                case DELETE:
                    drawDelete(g);
                    break;
                default:
                    throw new IllegalStateException("Unknown icon type: " + type);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawAdd(Graphics2D g) {
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(9, 4, 9, 14);
        g.drawLine(4, 9, 14, 9);
    }

    private void drawClipboard(Graphics2D g, boolean down) {
        g.drawRoundRect(3, 3, 12, 13, 2, 2);
        g.fillRoundRect(6, 1, 6, 4, 2, 2);
        if (down) {
            g.drawLine(9, 6, 9, 12);
            g.drawLine(6, 10, 9, 13);
            g.drawLine(12, 10, 9, 13);
        } else {
            g.drawLine(9, 13, 9, 7);
            g.drawLine(6, 9, 9, 6);
            g.drawLine(12, 9, 9, 6);
        }
    }

    private void drawPencil(Graphics2D g) {
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        g.drawLine(5, 13, 13, 5);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 15, 6, 14);
        g.drawLine(12, 3, 15, 6);
    }

    private void drawClone(Graphics2D g) {
        g.drawRoundRect(2, 3, 10, 10, 2, 2);
        g.drawRoundRect(6, 6, 10, 10, 2, 2);
    }

    private void drawDelete(Graphics2D g) {
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 5, 15, 5);
        g.drawLine(7, 3, 11, 3);
        g.drawRoundRect(5, 6, 8, 10, 1, 1);
        g.drawLine(8, 8, 8, 14);
        g.drawLine(10, 8, 10, 14);
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
