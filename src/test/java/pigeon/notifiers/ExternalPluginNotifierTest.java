package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.ExternalScreenshotPolicy;
import pigeon.message.Field;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.ExternalNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.client.events.PluginMessage;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class ExternalPluginNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    ExternalPluginNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // update config mocks
        when(config.notifyExternal()).thenReturn(true);
        when(config.externalSendImage()).thenReturn(ExternalScreenshotPolicy.REQUESTED);
    }

    @Test
    void testNotify() {
        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", samplePayload(null)));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("Hello %TARGET% from %USERNAME%")
                        .replacement("%TARGET%", Replacements.ofText("world"))
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testProfilesApplyIndependentExternalScreenshotPolicies() {
        PigeonProfile clan = externalProfile("Clan", true, ExternalScreenshotPolicy.NEVER,
            "https://example.com/clan");
        PigeonProfile friends = externalProfile("Friends", true, ExternalScreenshotPolicy.ALWAYS,
            "https://example.com/friends");
        PigeonProfile disabled = externalProfile("Disabled", false, ExternalScreenshotPolicy.ALWAYS,
            "https://example.com/disabled");
        mockStoredProfiles(clan, friends, disabled);

        plugin.onPluginMessage(new PluginMessage("dink", "notify", samplePayload(null)));

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> images = ArgumentCaptor.forClass(Boolean.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), images.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(false, true), images.getAllValues());
    }

    @Test
    void testNoReplacements() {
        // fire event
        var payload = samplePayload(null);
        payload.remove("replacements");
        payload.put("text", "text with no replacements");
        plugin.onPluginMessage(new PluginMessage("dink", "notify", payload));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("text with no replacements")
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testMissingReplacement() {
        // fire event
        var payload = samplePayload(null);
        payload.remove("replacements");
        payload.put("text", "text with no custom replacement %USERNAME%");
        plugin.onPluginMessage(new PluginMessage("dink", "notify", payload));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("text with no custom replacement %USERNAME%")
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testPatternWithoutReplacement() {
        // fire event
        var payload = samplePayload(null);
        payload.remove("replacements");
        payload.put("text", "text with no custom replacement %XD%");
        plugin.onPluginMessage(new PluginMessage("dink", "notify", payload));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("text with no custom replacement %XD%")
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testFallbackUrl() {
        // update config mocks
        String url = "https://example.com/";
        when(config.externalWebhook()).thenReturn(url);

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", samplePayload(null)));

        // verify notification
        verifyCreateMessage(
            url,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("Hello %TARGET% from %USERNAME%")
                        .replacement("%TARGET%", Replacements.ofText("world"))
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testImage() {
        // update config mocks
        when(config.externalSendImage()).thenReturn(ExternalScreenshotPolicy.ALWAYS);

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", samplePayload(null)));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            true,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("Hello %TARGET% from %USERNAME%")
                        .replacement("%TARGET%", Replacements.ofText("world"))
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testRequestImage() {
        // prepare payload
        var data = samplePayload(null);
        data.put("imageRequested", true);

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", data));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            true,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("Hello %TARGET% from %USERNAME%")
                        .replacement("%TARGET%", Replacements.ofText("world"))
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testRequestImageDenied() {
        // update config mocks
        when(config.externalSendImage()).thenReturn(ExternalScreenshotPolicy.NEVER);

        // prepare payload
        var data = samplePayload(null);
        data.put("imageRequested", true);

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", data));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.EXTERNAL_PLUGIN)
                .playerName(PLAYER_NAME)
                .customTitle("My Title")
                .customFooter("Sent by MyExternalPlugin via Pigeon")
                .text(
                    Template.builder()
                        .template("Hello %TARGET% from %USERNAME%")
                        .replacement("%TARGET%", Replacements.ofText("world"))
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .build()
                )
                .extra(new ExternalNotificationData("MyExternalPlugin", List.of(new Field("sample key", "sample value", null)), Collections.singletonMap("hello", "world")))
                .build()
        );
    }

    @Test
    void testIgnoreNamespace() {
        // fire event
        plugin.onPluginMessage(new PluginMessage("DANK", "notify", samplePayload("https://example.com/")));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreName() {
        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "donk", samplePayload("https://example.com/")));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreBadUrls() {
        // prepare payload
        var data = samplePayload(null);
        data.put("urls", List.of("https://example.com/"));

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", data));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // update config mocks
        when(config.notifyExternal()).thenReturn(false);

        // fire event
        plugin.onPluginMessage(new PluginMessage("dink", "notify", samplePayload("https://example.com/")));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private static Map<String, Object> samplePayload(String url) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", "Hello %TARGET% from %USERNAME%");
        data.put("title", "My Title");
        data.put("thumbnail", "not a url . com");
        data.put("fields", List.of(createField("sample key", "sample value")));
        data.put("replacements", Map.of("%TARGET%", createTextReplacement("world")));
        data.put("metadata", Map.of("hello", "world"));
        data.put("sourcePlugin", "MyExternalPlugin");
        if (url != null) {
            data.put("urls", Collections.singletonList(HttpUrl.parse(url)));
        }
        return data;
    }

    private static Map<String, Object> createField(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    private static Map<String, String> createTextReplacement(String text) {
        return Map.of("value", text);
    }

    private PigeonProfile externalProfile(String name, boolean enabled,
                                          ExternalScreenshotPolicy policy, String webhook) {
        return PigeonProfile.builder().schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID()).name(name).enabled(enabled)
            .settings(Map.of(
                "notifyExternal", new JsonPrimitive(true),
                "externalSendImage", new JsonPrimitive(policy.name())))
            .webhooks(new ProfileWebhooks(List.of(), Map.of("externalWebhook", List.of(webhook))))
            .build();
    }

    private void mockStoredProfiles(PigeonProfile... profiles) {
        profileRuntimeService.invalidate();
        List<UUID> ids = Arrays.stream(profiles).map(PigeonProfile::getId)
            .collect(java.util.stream.Collectors.toList());
        when(configManager.getConfiguration(ConfigProfileRepository.CONFIG_GROUP, ConfigProfileRepository.INDEX_KEY))
            .thenReturn(gson.toJson(ids));
        for (PigeonProfile profile : profiles) {
            when(configManager.getConfiguration(ConfigProfileRepository.CONFIG_GROUP, "profile_" + profile.getId()))
                .thenReturn(gson.toJson(profile));
        }
    }

}
