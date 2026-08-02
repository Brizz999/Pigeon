package pigeon.profiles;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ProfileCodec {
    public static final int MAX_NAME_LENGTH = 80;

    private final Gson gson;

    @Inject
    public ProfileCodec(Gson gson) {
        this.gson = gson;
    }

    public String toJson(PigeonProfile profile, boolean includeSecrets) {
        validate(profile);
        return gson.toJson(includeSecrets ? profile : profile.withoutSecrets());
    }

    public PigeonProfile fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new ProfileValidationException("Profile JSON cannot be empty");
        }

        final PigeonProfile profile;
        try {
            profile = gson.fromJson(json, PigeonProfile.class);
        } catch (JsonParseException exception) {
            throw new ProfileValidationException("Profile JSON is malformed", exception);
        }

        validate(profile);
        return profile.toBuilder()
            .settings(Collections.unmodifiableMap(new LinkedHashMap<>(profile.getSettings())))
            .webhooks(new ProfileWebhooks(profile.getWebhooks().getPrimary(), profile.getWebhooks().getOverrides()))
            .build();
    }

    public void validate(PigeonProfile profile) {
        if (profile == null) {
            throw new ProfileValidationException("Profile is required");
        }
        if (profile.getSchemaVersion() != PigeonProfile.CURRENT_SCHEMA_VERSION) {
            throw new ProfileValidationException("Unsupported profile schema version: " + profile.getSchemaVersion());
        }
        if (profile.getId() == null) {
            throw new ProfileValidationException("Profile ID is required");
        }

        String name = profile.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new ProfileValidationException("Profile name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ProfileValidationException("Profile name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (!name.equals(name.trim())) {
            throw new ProfileValidationException("Profile name cannot start or end with whitespace");
        }
        if (profile.getSettings() == null) {
            throw new ProfileValidationException("Profile settings are required");
        }
        validateWebhooks(profile.getWebhooks());
    }

    private void validateWebhooks(ProfileWebhooks webhooks) {
        if (webhooks == null) {
            throw new ProfileValidationException("Profile webhooks are required");
        }

        validateUrlList(webhooks.getPrimary(), "primary webhook");
        Map<String, List<String>> overrides = webhooks.getOverrides();
        if (overrides == null) {
            throw new ProfileValidationException("Webhook overrides are required");
        }
        overrides.forEach((notifier, urls) -> {
            if (notifier == null || notifier.trim().isEmpty()) {
                throw new ProfileValidationException("Webhook override notifier key cannot be empty");
            }
            validateUrlList(urls, notifier + " webhook override");
        });
    }

    private void validateUrlList(List<String> urls, String label) {
        if (urls == null) {
            throw new ProfileValidationException("The " + label + " list is required");
        }
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                throw new ProfileValidationException("The " + label + " list contains an empty URL");
            }
            try {
                URI uri = new URI(url);
                String scheme = uri.getScheme();
                if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                    throw new ProfileValidationException("The " + label + " must use an HTTP(S) URL");
                }
            } catch (URISyntaxException exception) {
                throw new ProfileValidationException("The " + label + " contains an invalid URL", exception);
            }
        }
    }
}
