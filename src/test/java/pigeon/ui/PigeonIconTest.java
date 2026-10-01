package pigeon.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PigeonIconTest {
    @Test
    void pluginHubIconIsValidTransparentPng() throws IOException {
        BufferedImage actual = ImageIO.read(new File("icon.png"));

        assertNotNull(actual);
        assertEquals(48, actual.getWidth());
        assertEquals(48, actual.getHeight());
        assertTrue(actual.getColorModel().hasAlpha());
        assertEquals(0, actual.getRGB(0, 0) >>> 24);
        assertEquals(255, actual.getRGB(24, 27) >>> 24);
        // Java2D antialiasing varies by platform and JDK. Do not compare the
        // checked-in PNG pixel-for-pixel with a fresh rendering of the artwork.
    }
}
