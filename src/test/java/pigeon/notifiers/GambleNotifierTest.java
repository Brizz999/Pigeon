package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.GambleNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import pigeon.util.ItemSearcher;
import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class GambleNotifierTest extends MockedNotifierTest {
    private static final int GRANITE_HELM_PRICE = 29_000;
    private static final int DRAGON_CHAINBODY_PRICE = 150_000;
    private static final int ELITE_CLUE_PRICE = 0;

    @Bind
    @InjectMocks
    GambleNotifier notifier;

    @Bind
    @Mock
    ItemSearcher itemSearcher;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        when(config.notifyGamble()).thenReturn(true);
        when(config.gambleSendImage()).thenReturn(true);
        when(config.gambleInterval()).thenReturn(10);
        when(config.gambleRareLoot()).thenReturn(true);
        when(config.gambleNotifyMessage()).thenReturn("%USERNAME% has reached %COUNT% high gambles");
        when(config.gambleRareNotifyMessage()).thenReturn("%USERNAME% has received rare loot at gamble count %COUNT%: \n\n%LOOT%");

        mockItem(ItemID.GRANITE_HELM, GRANITE_HELM_PRICE, "Granite helm");
        mockItem(ItemID.DRAGON_CHAINBODY, DRAGON_CHAINBODY_PRICE, "Dragon chainbody");
        mockItem(ItemID.TRAIL_ELITE_EMOTE_EXP1, ELITE_CLUE_PRICE, "Clue scroll (elite)");
        when(itemSearcher.findItemId("Granite helm")).thenReturn(ItemID.GRANITE_HELM);
        when(itemSearcher.findItemId("Dragon chainbody")).thenReturn(ItemID.DRAGON_CHAINBODY);
        when(itemSearcher.findItemId("Clue scroll (elite)")).thenReturn(ItemID.TRAIL_ELITE_EMOTE_EXP1);
    }

    @Test
    void testNotifyInterval() {
        notifier.onMesBoxNotification("Granite helm! High level gamble count: 20.");

        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            true,
            NotificationBody.builder()
                .text(buildTemplate(PLAYER_NAME + " has reached 20 high gambles"))
                .extra(new GambleNotificationData(20, Collections.singletonList(new SerializedItemStack(ItemID.GRANITE_HELM, 1, GRANITE_HELM_PRICE, "Granite helm"))))
                .type(NotificationType.BARBARIAN_ASSAULT_GAMBLE)
                .build()
        );
    }

    @Test
    void testTertiaryLoot() {
        notifier.onMesBoxNotification("Granite helm! Clue scroll (elite)! High level gamble count: 10.");

        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            true,
            NotificationBody.builder()
                .text(buildTemplate(PLAYER_NAME + " has reached 10 high gambles"))
                .extra(new GambleNotificationData(10, Arrays.asList(
                    new SerializedItemStack(ItemID.GRANITE_HELM, 1, GRANITE_HELM_PRICE, "Granite helm"),
                    new SerializedItemStack(ItemID.TRAIL_ELITE_EMOTE_EXP1, 1, ELITE_CLUE_PRICE, "Clue scroll (elite)")
                )))
                .type(NotificationType.BARBARIAN_ASSAULT_GAMBLE)
                .build()
        );
    }

    @Test
    void testIgnoredInterval() {
        notifier.onMesBoxNotification("Watermelon seed (x 50)! High level gamble count: 21.");
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testRareLoot() {
        notifier.onMesBoxNotification("Dragon chainbody! High level gamble count: 11.");

        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            true,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(PLAYER_NAME + " has received rare loot at gamble count 11: \n\n1 x {{dchain}} (150K)")
                        .replacement("{{dchain}}", Replacements.ofWiki("Dragon chainbody"))
                        .build()
                )
                .extra(new GambleNotificationData(11, Collections.singletonList(new SerializedItemStack(ItemID.DRAGON_CHAINBODY, 1, DRAGON_CHAINBODY_PRICE, "Dragon chainbody"))))
                .type(NotificationType.BARBARIAN_ASSAULT_GAMBLE)
                .build()
        );
    }

    @Test
    void testNotifyProfilesWithIndependentIntervalsAndRarePolicy() {
        PigeonProfile interval = gambleProfile("Interval", true, 11, false,
            "Interval %COUNT%", "https://example.com/interval");
        PigeonProfile rare = gambleProfile("Rare", true, 100, true,
            "Rare %LOOT%", "https://example.com/rare");
        PigeonProfile ignored = gambleProfile("Ignored", true, 100, false,
            "Ignored", "https://example.com/ignored");
        mockStoredProfiles(interval, rare, ignored);

        notifier.onMesBoxNotification("Dragon chainbody! High level gamble count: 11.");

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/interval", "https://example.com/rare"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals("Interval 11", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertTrue(bodies.getAllValues().get(1).getText().evaluate(false).startsWith("Rare 1 x"));
    }

    @Test
    void testIgnoredRareLootInterval() {
        when(config.gambleRareLoot()).thenReturn(false);
        notifier.onMesBoxNotification("Dragon chainbody! High level gamble count: 13.");
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        when(config.notifyGamble()).thenReturn(false);
        notifier.onMesBoxNotification("Dragon chainbody! High level gamble count: 100.");
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private PigeonProfile gambleProfile(String name, boolean enabled, int interval,
                                        boolean rareLoot, String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID()).name(name).enabled(enabled)
            .settings(Map.of(
                "gambleEnabled", new JsonPrimitive(true),
                "gambleSendImage", new JsonPrimitive(false),
                "gambleInterval", new JsonPrimitive(interval),
                "gambleRareLoot", new JsonPrimitive(rareLoot),
                "gambleNotifMessage", new JsonPrimitive(message),
                "gambleRareNotifMessage", new JsonPrimitive(message)))
            .webhooks(new ProfileWebhooks(List.of(webhook), Map.of())).build();
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
