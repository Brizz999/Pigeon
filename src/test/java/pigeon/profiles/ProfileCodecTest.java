package pigeon.profiles;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCodecTest {
    private ProfileCodec codec;

    @BeforeEach
    void setUp() {
        codec = new ProfileCodec(new Gson());
    }

    @Test
    void roundTripsProfileWithSecrets() {
        PigeonProfile profile = profileWithWebhook();

        PigeonProfile decoded = codec.fromJson(codec.toJson(profile, true));

        assertEquals(profile, decoded);
    }

    @Test
    void redactsWebhookSecretsByDefault() {
        String json = codec.toJson(profileWithWebhook(), false);

        assertFalse(json.contains("discord.com/api/webhooks"));
        PigeonProfile decoded = codec.fromJson(json);
        assertTrue(decoded.getWebhooks().getPrimary().isEmpty());
        assertTrue(decoded.getWebhooks().getOverrides().isEmpty());
    }

    @Test
    void rejectsUnsupportedSchema() {
        String json = codec.toJson(profileWithWebhook(), true)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":2");

        assertThrows(ProfileValidationException.class, () -> codec.fromJson(json));
    }

    @Test
    void rejectsNonHttpWebhook() {
        PigeonProfile profile = profileWithWebhook().toBuilder()
            .webhooks(new ProfileWebhooks(List.of("file:///secret"), Collections.emptyMap()))
            .build();

        assertThrows(ProfileValidationException.class, () -> codec.toJson(profile, true));
    }

    @Test
    void retainsUnknownSettings() {
        PigeonProfile profile = profileWithWebhook().toBuilder()
            .settings(Map.of("futureSetting", new JsonParser().parse("{\"mode\":\"new\"}")))
            .build();

        PigeonProfile decoded = codec.fromJson(codec.toJson(profile, true));

        assertEquals(profile.getSettings().get("futureSetting"), decoded.getSettings().get("futureSetting"));
    }

    private static PigeonProfile profileWithWebhook() {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.fromString("4f0fbb88-3c2b-4db0-8ad4-b34ec2c3995e"))
            .name("Clan server")
            .enabled(true)
            .settings(Collections.emptyMap())
            .webhooks(new ProfileWebhooks(
                List.of("https://discord.com/api/webhooks/123/secret"),
                Map.of("loot", List.of("https://example.com/loot"))
            ))
            .build();
    }
}
