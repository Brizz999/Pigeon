package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.SettingsManager;
import pigeon.domain.SeasonalPolicy;
import pigeon.message.DiscordMessageHandler;
import pigeon.message.NotificationBody;
import pigeon.util.AccountTypeTracker;
import pigeon.util.Utils;
import pigeon.util.WorldTypeTracker;
import pigeon.util.WorldUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public abstract class BaseNotifier {

    @Inject
    protected PigeonConfig config;

    @Inject
    protected AccountTypeTracker accountTracker;

    @Inject
    protected WorldTypeTracker worldTracker;

    @Inject
    protected Client client;

    @Inject
    protected EventBus eventBus;

    @Inject
    protected ScheduledExecutorService executor;

    @Inject
    protected DiscordMessageHandler messageHandler;

    public boolean isEnabled() {
        return worldTracker.hasValidState() && accountTracker.hasValidState();
    }

    protected abstract String getWebhookUrl();

    protected final void createMessage(boolean sendImage, NotificationBody<?> body) {
        this.createMessage(getWebhookUrl(), sendImage, body);
    }

    protected final void createMessage(String overrideUrl, boolean sendImage, NotificationBody<?> body) {
        // Preserve Dink's legacy behavior of invoking the handler even for a blank URL.
        messageHandler.createMessage(resolveWebhook(config, overrideUrl), sendImage, body);
        publishPluginMessage(body);
    }

    protected final boolean deliver(PigeonConfig deliveryConfig, String overrideUrl, boolean sendImage,
                                    NotificationBody<?> body) {
        String url = resolveWebhook(deliveryConfig, overrideUrl);
        if (StringUtils.isBlank(url)) {
            return false;
        }

        messageHandler.createMessage(deliveryConfig, url, sendImage, body);
        return true;
    }

    private String resolveWebhook(PigeonConfig deliveryConfig, String overrideUrl) {
        String override;
        if (StringUtils.isNotBlank(deliveryConfig.leaguesWebhook())
            && deliveryConfig.seasonalPolicy() == SeasonalPolicy.FORWARD_TO_LEAGUES
            && WorldUtils.isSeasonal(client)) {
            override = deliveryConfig.leaguesWebhook();
        } else {
            override = overrideUrl;
        }
        return StringUtils.isNotBlank(override) ? override : deliveryConfig.primaryWebhook();
    }

    protected final void publishPluginMessage(NotificationBody<?> body) {
        // notify other hub plugins
        var playerName = body.getPlayerName() != null ? body.getPlayerName() : Utils.getPlayerName(client);
        var accountType = body.getAccountType() != null ? body.getAccountType() : Utils.getAccountType(client);
        executor.execute(() -> {
            Map<String, Object> metadata = body.getExtra() != null ? new HashMap<>(body.getExtra().sanitized()) : new HashMap<>();
            metadata.put("playerName", playerName);
            metadata.put("accountType", String.valueOf(accountType));
            metadata.put("plainText", body.getText().evaluate(false));
            var payload = new PluginMessage(SettingsManager.CONFIG_GROUP, body.getType().name(), Collections.unmodifiableMap(metadata));
            eventBus.post(payload);
        });
    }

}
