package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.SlayerNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlayerNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    SlayerNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifySlayer()).thenReturn(true);
        when(config.slayerSendImage()).thenReturn(false);
        when(config.slayerPointThreshold()).thenReturn(1);
        when(config.slayerNotifyMessage()).thenReturn("%USERNAME% has completed: %TASK%, getting %POINTS% points for a total %TASKCOUNT% tasks completed");
    }

    @Test
    void testNotify() {
        // fire chat messages
        notifier.onChatMessage("You have completed your task! You killed 1 TzTok-Jad. You gained 69,420 xp.");
        notifier.onChatMessage("You've completed 100 tasks and received 10 points, giving you a total of 200; return to a Slayer master.");

        // check notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(1, "TzTok-Jad", 10, 100))
                .extra(new SlayerNotificationData("1 TzTok-Jad", "100", "10", 1, "TzTok-Jad"))
                .type(NotificationType.SLAYER)
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneTrackedTask() {
        PigeonProfile clan = slayerProfile(
            "Clan", true, 1, "Clan: %TASK% for %POINTS%", "https://example.com/clan");
        PigeonProfile friends = slayerProfile(
            "Friends", true, 10, "Friends: %TASK% for %POINTS%", "https://example.com/friends");
        PigeonProfile disabled = slayerProfile(
            "Disabled", false, 0, "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, disabled);

        notifier.onChatMessage("You have completed your task! You killed 1 TzTok-Jad. You gained 69,420 xp.");
        notifier.onChatMessage("You've completed 100 tasks and received 10 points, giving you a total of 200; return to a Slayer master.");

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: 1 TzTok-Jad for 10", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: 1 TzTok-Jad for 10", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testNotifyThousand() {
        when(config.slayerPointThreshold()).thenReturn(1000);

        // fire chat messages
        notifier.onChatMessage("You have completed your task! You killed 245 Cave Kraken. You gained 62,475 xp.");
        notifier.onChatMessage("You've completed 1,000 tasks and received 1,000 points, giving you a total of 2,584, return to a Slayer master.");

        // check notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(245, "Cave Kraken", "1,000", "1,000"))
                .extra(new SlayerNotificationData("245 Cave Kraken", "1,000", "1,000", 245, "Cave Kraken"))
                .type(NotificationType.SLAYER)
                .build()
        );
    }

    @Test
    void testNotifyBoss() {
        // fire chat messages
        notifier.onChatMessage("You are granted an extra reward of 5k Slayer XP for completing your boss task against the Cave Kraken boss.");
        notifier.onChatMessage("You have completed your task! You killed 50 Bosses. You gained 14,200 xp.");
        notifier.onChatMessage("You've completed 150 tasks and received 10 points, giving you a total of 200; return to a Slayer master.");

        // check notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(50, "Cave Kraken", 10, 150))
                .extra(new SlayerNotificationData("50 Cave Kraken", "150", "10", 50, "Cave Kraken"))
                .type(NotificationType.SLAYER)
                .build()
        );
    }

    @Test
    void testNotifyBarrows() {
        // fire chat messages
        notifier.onChatMessage("You are granted an extra reward of 5k Slayer XP for completing your boss task against Barrows brothers.");
        notifier.onChatMessage("You have completed your task! You killed 36 Bosses. You gained 8,943 xp.");
        notifier.onChatMessage("You've completed 881 tasks and received 20 points, giving you a total of 689; return to a Slayer master.");

        // check notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(36, "Barrows brothers", 20, 881))
                .extra(new SlayerNotificationData("36 Barrows brothers", "881", "20", 36, "Barrows brothers"))
                .type(NotificationType.SLAYER)
                .build()
        );
    }

    @Test
    void testNotifyChaos() {
        // fire chat messages
        notifier.onChatMessage("You are granted an extra reward of 5k Slayer XP for completing your boss task against the Chaos Elemental.");
        notifier.onChatMessage("You have completed your task! You killed 11 Bosses. You gained 7,954 xp.");
        notifier.onChatMessage("You've completed 242 tasks and received 15 points, giving you a total of 3,254; return to a Slayer master.");

        // check notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(11, "Chaos Elemental", 15, 242))
                .extra(new SlayerNotificationData("11 Chaos Elemental", "242", "15", 11, "Chaos Elemental"))
                .type(NotificationType.SLAYER)
                .build()
        );
    }

    @Test
    void testIgnorePoints() {
        // fire chat messages
        notifier.onChatMessage("You have completed your task! You killed 1 TzTok-Jad. You gained 69,420 xp.");
        notifier.onChatMessage("You've completed 101 tasks and received 0 points, giving you a total of 200; return to a Slayer master.");

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // disable notifier
        when(config.notifySlayer()).thenReturn(false);

        // fire chat messages
        notifier.onChatMessage("You have completed your task! You killed 1 TzTok-Jad. You gained 69,420 xp.");
        notifier.onChatMessage("You've completed 100 tasks and received 10 points, giving you a total of 200; return to a Slayer master.");

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private static Template buildTemplate(int count, String monster, Object points, Object completed) {
        return Template.builder()
            .template(String.format("%s has completed: %d {{monster}}, getting %s points for a total %s tasks completed", PLAYER_NAME, count, points, completed))
            .replacement("{{monster}}", Replacements.ofWiki(monster))
            .build();
    }

    private PigeonProfile slayerProfile(String name, boolean enabled, int threshold,
                                        String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "slayerEnabled", new JsonPrimitive(true),
                "slayerSendImage", new JsonPrimitive(false),
                "slayerPointThreshold", new JsonPrimitive(threshold),
                "slayerNotifMessage", new JsonPrimitive(message)
            ))
            .webhooks(new ProfileWebhooks(List.of(webhook), Map.of()))
            .build();
    }

    private void mockStoredProfiles(PigeonProfile... profiles) {
        List<UUID> ids = Arrays.stream(profiles)
            .map(PigeonProfile::getId)
            .collect(java.util.stream.Collectors.toList());
        when(configManager.getConfiguration(ConfigProfileRepository.CONFIG_GROUP, ConfigProfileRepository.INDEX_KEY))
            .thenReturn(gson.toJson(ids));
        for (PigeonProfile profile : profiles) {
            when(configManager.getConfiguration(
                ConfigProfileRepository.CONFIG_GROUP, "profile_" + profile.getId()))
                .thenReturn(gson.toJson(profile));
        }
    }
}
