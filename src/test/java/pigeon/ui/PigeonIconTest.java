package pigeon.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PigeonIconTest {
    @Test
    void pluginHubIconMatchesPigeonArtwork() throws IOException {
        BufferedImage expected = PigeonIcon.create(48);
        BufferedImage actual = ImageIO.read(new File("icon.png"));

        assertEquals(48, actual.getWidth());
        assertEquals(48, actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y),
                    "Plugin Hub icon differs at (" + x + ", " + y + ")");
            }
        }
    }
}
