package pigeon;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PigeonConfigVisibilityTest {
    @Test
    void profileOwnedSettingsAreHiddenFromRuneLiteConfigPanel() {
        List<String> visibleItems = Arrays.stream(PigeonConfig.class.getDeclaredMethods())
            .map(method -> new ConfigMethod(method, method.getAnnotation(ConfigItem.class)))
            .filter(configMethod -> configMethod.item != null && !configMethod.item.hidden())
            .map(configMethod -> configMethod.method.getName())
            .sorted()
            .collect(Collectors.toList());

        assertEquals(List.of("showPanelIcon"), visibleItems,
            "Only global interface settings should appear in the generic RuneLite config panel");

        List<String> visibleSections = Arrays.stream(PigeonConfig.class.getDeclaredFields())
            .filter(field -> field.getAnnotation(ConfigSection.class) != null)
            .map(field -> field.getName())
            .sorted()
            .collect(Collectors.toList());

        assertTrue(visibleSections.isEmpty(),
            "The generic RuneLite config panel must not render empty profile sections: " + visibleSections);
    }

    private static final class ConfigMethod {
        private final Method method;
        private final ConfigItem item;

        private ConfigMethod(Method method, ConfigItem item) {
            this.method = method;
            this.item = item;
        }
    }
}
