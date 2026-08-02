package pigeon.notifiers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import pigeon.domain.ExternalNotificationRequest;
import pigeon.domain.ExternalScreenshotPolicy;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.ExternalNotificationData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.HttpUrlAdapter;
import pigeon.util.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import okhttp3.HttpUrl;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Singleton
public class ExternalPluginNotifier extends BaseNotifier {

    private ClientThread clientThread;
    private Gson gson;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyExternal() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.externalWebhook();
    }

    @Inject
    void init(ClientThread clientThread, Gson gson) {
        this.clientThread = clientThread;
        this.gson = gson.newBuilder()
            .registerTypeAdapter(HttpUrl.class, new HttpUrlAdapter())
            .create();
    }

    public void onNotify(Map<String, Object> data) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (!enabled) {
            log.debug("Skipping requested external Pigeon notification since notifier is disabled: {}", data);
            return;
        }

        // ensure input urls are specified correctly
        if (!isUrlInputValid(data.get("urls"))) {
            log.warn("Skipping externally requested Pigeon notification due to invalid 'urls' format from {}", data.get("sourcePlugin"));
            return;
        }

        // parse request
        ExternalNotificationRequest input;
        try {
            input = gson.fromJson(gson.toJsonTree(data), ExternalNotificationRequest.class);
        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse requested webhook notification from an external plugin: {}", data, e);
            return;
        }

        // validate request
        if (input.getSourcePlugin() == null || input.getSourcePlugin().isBlank()) {
            log.info("Skipping externally requested Pigeon notification due to missing 'sourcePlugin': {}", data);
            return;
        }

        if (input.getText() == null || input.getText().isBlank()) {
            log.info("Skipping externally requested Pigeon notification due to missing 'text': {}", data);
            return;
        }

        if (input.getThumbnail() != null && HttpUrl.parse(input.getThumbnail()) == null) {
            log.debug("Replacing invalid thumbnail url: {}", input.getThumbnail());
            input.setThumbnail(NotificationType.EXTERNAL_PLUGIN.getThumbnail());
        }

        // process request
        this.handleNotify(input, profiles);
    }

    private void handleNotify(ExternalNotificationRequest input, ProfileRuntimeSnapshot profiles) {
        var player = Utils.getPlayerName(client);
        var template = Template.builder()
            .template(input.getText())
            .replacements(Objects.requireNonNullElseGet(input.getReplacements(), () -> new HashMap<>(2)))
            .replacement("%USERNAME%", Replacements.ofText(player))
            .build();

        var footer = String.format("Sent by %s via Pigeon", input.getSourcePlugin());

        var body = NotificationBody.builder()
            .type(NotificationType.EXTERNAL_PLUGIN)
            .playerName(player)
            .text(template)
            .customTitle(input.getTitle())
            .customFooter(footer)
            .thumbnailUrl(input.getThumbnail())
            .extra(new ExternalNotificationData(input.getSourcePlugin(), input.getFields(), input.getMetadata()))
            .build();

        clientThread.invoke(() -> {
            if (profiles.isProfilesConfigured()) {
                NotificationBody<?> outbound = null;
                for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
                    if (!isProfileEnabled(profile)) continue;
                    String urls = input.getUrls(profile::externalWebhook);
                    if (deliver(profile, urls, shouldSendImage(profile.externalSendImage(), input), body)
                        && outbound == null) outbound = body;
                }
                if (outbound != null) publishPluginMessage(outbound);
                return;
            }
            var urls = input.getUrls(this::getWebhookUrl);
            createMessage(urls, shouldSendImage(config.externalSendImage(), input), body);
        });
    }

    private boolean shouldSendImage(ExternalScreenshotPolicy policy, ExternalNotificationRequest input) {
        return policy != ExternalScreenshotPolicy.NEVER
            && client.getGameState().getState() >= GameState.LOGGING_IN.getState()
            && (policy == ExternalScreenshotPolicy.ALWAYS || input.isImageRequested());
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyExternal() && profileEligibilityService.isEligible(profile);
    }

    private static boolean isUrlInputValid(Object urls) {
        if (urls == null) {
            return true; // use URLs specified in Pigeon's config
        }
        if (!(urls instanceof Iterable)) {
            return false; // we try to convert to list in ExternalNotificationRequest
        }
        for (Object url : (Iterable<?>) urls) {
            if (!(url instanceof HttpUrl))
                return false; // received non-HttpUrl; input should be rejected
        }
        return true; // all elements are HttpUrl instances; proceed as normal
    }

}
