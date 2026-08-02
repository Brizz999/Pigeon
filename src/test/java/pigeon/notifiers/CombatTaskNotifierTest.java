package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.CombatAchievementTier;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.CombatAchievementData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.gameval.VarbitID;
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

class CombatTaskNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    CombatTaskNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyCombatTask()).thenReturn(true);
        when(config.combatTaskSendImage()).thenReturn(false);
        when(config.combatTaskMessage()).thenReturn("%USERNAME% has completed %TIER% combat task: %TASK%");
        when(config.combatTaskUnlockMessage()).thenReturn("%USERNAME% has unlocked the rewards for the %COMPLETED% tier, by completing the combat task: %TASK%");
        when(config.minCombatAchievementTier()).thenReturn(CombatAchievementTier.HARD);

        // init client mocks
        when(client.getVarbitValue(
            CombatTaskNotifier.CUM_POINTS_VARBIT_BY_TIER.get(CombatAchievementTier.EASY)
        )).thenReturn(33);
        when(client.getVarbitValue(
            CombatTaskNotifier.CUM_POINTS_VARBIT_BY_TIER.get(CombatAchievementTier.MEDIUM)
        )).thenReturn(115);
        when(client.getVarbitValue(
            CombatTaskNotifier.CUM_POINTS_VARBIT_BY_TIER.get(CombatAchievementTier.HARD)
        )).thenReturn(304);
        when(client.getVarbitValue(
            CombatTaskNotifier.CUM_POINTS_VARBIT_BY_TIER.get(CombatAchievementTier.MASTER)
        )).thenReturn(1465);
        when(client.getVarbitValue(
            CombatTaskNotifier.CUM_POINTS_VARBIT_BY_TIER.get(CombatAchievementTier.GRANDMASTER)
        )).thenReturn(2005);
    }

    @Test
    void testNotify() {
        // update mock
        when(client.getVarbitValue(VarbitID.CA_POINTS)).thenReturn(200);

        // send fake message
        notifier.onTick();
        notifier.onGameMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

        // verify handled
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(String.format("%s has completed %s combat task: {{task}}", PLAYER_NAME, "Hard"))
                        .replacement("{{task}}", Replacements.ofWiki("Whack-a-Mole"))
                        .build()
                )
                .extra(new CombatAchievementData(CombatAchievementTier.HARD, "Whack-a-Mole", 3, 200, 85, 189, 2005,  CombatAchievementTier.MEDIUM, CombatAchievementTier.HARD, null))
                .playerName(PLAYER_NAME)
                .type(NotificationType.COMBAT_ACHIEVEMENT)
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneCombatTask() {
        PigeonProfile clan = combatProfile(
            "Clan", true, CombatAchievementTier.EASY,
            "Clan: %TIER% %TASK%", "https://example.com/clan");
        PigeonProfile friends = combatProfile(
            "Friends", true, CombatAchievementTier.HARD,
            "Friends: %TIER% %TASK%", "https://example.com/friends");
        PigeonProfile elite = combatProfile(
            "Elite", true, CombatAchievementTier.ELITE,
            "Elite", "https://example.com/elite");
        PigeonProfile disabled = combatProfile(
            "Disabled", false, CombatAchievementTier.EASY,
            "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, elite, disabled);

        when(client.getVarbitValue(VarbitID.CA_POINTS)).thenReturn(200);
        notifier.onTick();
        notifier.onGameMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: Hard Whack-a-Mole", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: Hard Whack-a-Mole", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testNotifyUnlock() {
        // init thresholds
        notifier.onTick();

        // calculate points
        int oldPoints = 1460;
        CombatAchievementTier taskTier = CombatAchievementTier.GRANDMASTER;
        int newPoints = oldPoints + taskTier.getPoints();
        when(client.getVarbitValue(VarbitID.CA_POINTS)).thenReturn(newPoints);

        // fire completion message
        notifier.onGameMessage("Congratulations, you've completed a grandmaster combat task: No Pressure (6 points).");

        // verify handled
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildUnlockTemplate("Master", "No Pressure"))
                .extra(new CombatAchievementData(CombatAchievementTier.GRANDMASTER, "No Pressure", 6, 1466, 1466 - 1465, 2005 - 1465, 2005, null, CombatAchievementTier.GRANDMASTER, CombatAchievementTier.MASTER))
                .playerName(PLAYER_NAME)
                .type(NotificationType.COMBAT_ACHIEVEMENT)
                .build()
        );
    }

    @Test
    void testNotifyUnlockGrand() {
        // init thresholds
        notifier.onTick();

        // calculate points
        int oldPoints = 1999;
        CombatAchievementTier taskTier = CombatAchievementTier.GRANDMASTER;
        int newPoints = oldPoints + taskTier.getPoints();
        when(client.getVarbitValue(VarbitID.CA_POINTS)).thenReturn(newPoints);

        // fire completion message
        notifier.onGameMessage("Congratulations, you've completed a grandmaster combat task: No Pressure (6 points).");

        // verify handled
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildUnlockTemplate("Grandmaster", "No Pressure"))
                .extra(new CombatAchievementData(CombatAchievementTier.GRANDMASTER, "No Pressure", 6, 2005, null, null, 2005, null, null, CombatAchievementTier.GRANDMASTER))
                .playerName(PLAYER_NAME)
                .type(NotificationType.COMBAT_ACHIEVEMENT)
                .build()
        );
    }

    @Test
    void testSkipped() {
        // send too easy achievement
        notifier.onGameMessage("Congratulations, you've completed an easy combat task: A Slow Death.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnore() {
        // send unrelated message
        notifier.onGameMessage("Congratulations, you've completed a gachi combat task: Swordfight with the homies.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // disable notifier
        when(config.notifyCombatTask()).thenReturn(false);

        // send fake message
        notifier.onGameMessage("Congratulations, you've completed a hard combat task: Whack-a-Mole.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private static Template buildUnlockTemplate(String tier, String task) {
        return Template.builder()
            .template(String.format("%s has unlocked the rewards for the %s tier, by completing the combat task: {{task}}", PLAYER_NAME, tier))
            .replacement("{{task}}", Replacements.ofWiki(task))
            .build();
    }

    private PigeonProfile combatProfile(String name, boolean enabled, CombatAchievementTier minimum,
                                        String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "combatTaskEnabled", new JsonPrimitive(true),
                "combatTaskSendImage", new JsonPrimitive(false),
                "combatTaskMinTier", new JsonPrimitive(minimum.name()),
                "combatTaskMessage", new JsonPrimitive(message),
                "combatTaskUnlockMessage", new JsonPrimitive(message)
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
