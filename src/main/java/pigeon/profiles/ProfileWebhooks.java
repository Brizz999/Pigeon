package pigeon.profiles;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class ProfileWebhooks {
    List<String> primary;
    Map<String, List<String>> overrides;

    public ProfileWebhooks(List<String> primary, Map<String, List<String>> overrides) {
        this.primary = primary == null ? null : List.copyOf(primary);
        if (overrides == null) {
            this.overrides = null;
        } else {
            Map<String, List<String>> copiedOverrides = new LinkedHashMap<>();
            overrides.forEach((key, urls) -> copiedOverrides.put(key, urls == null ? null : List.copyOf(urls)));
            this.overrides = Collections.unmodifiableMap(copiedOverrides);
        }
    }

    public static ProfileWebhooks empty() {
        return new ProfileWebhooks(Collections.emptyList(), Collections.emptyMap());
    }
}
