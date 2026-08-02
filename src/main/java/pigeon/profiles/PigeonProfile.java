package pigeon.profiles;

import com.google.gson.JsonElement;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
public class PigeonProfile {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    int schemaVersion;
    UUID id;
    String name;
    boolean enabled;
    Map<String, JsonElement> settings;
    ProfileWebhooks webhooks;

    public static PigeonProfile create(String name) {
        return PigeonProfile.builder()
            .schemaVersion(CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(true)
            .settings(Collections.emptyMap())
            .webhooks(ProfileWebhooks.empty())
            .build();
    }

    public PigeonProfile copyAs(String newName) {
        return toBuilder()
            .id(UUID.randomUUID())
            .name(newName)
            .build();
    }

    public PigeonProfile withoutSecrets() {
        return toBuilder()
            .webhooks(ProfileWebhooks.empty())
            .build();
    }
}
