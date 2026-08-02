package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.AccountType;
import pigeon.domain.LeagueRelicTier;
import pigeon.domain.LeagueTaskDifficulty;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.LeaguesAreaNotificationData;
import pigeon.notifiers.data.LeaguesMasteryNotificationData;
import pigeon.notifiers.data.LeaguesRelicNotificationData;
import pigeon.notifiers.data.LeaguesTaskNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled; // unused when there's an active leagues
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pigeon.notifiers.LeaguesNotifier.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class LeaguesNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    LeaguesNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // client mocks
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(AccountType.IRONMAN.ordinal());
        when(client.getVarbitValue(VarbitID.LEAGUE_TYPE)).thenReturn(LeaguesNotifier.CURRENT_LEAGUE_VERSION);

        // config mocks
        when(config.notifyLeagues()).thenReturn(true);
        when(config.leaguesAreaUnlock()).thenReturn(true);
        when(config.leaguesRelicUnlock()).thenReturn(true);
        when(config.leaguesTaskCompletion()).thenReturn(true);
        when(config.leaguesMasteryUnlock()).thenReturn(true);
        when(config.leaguesTaskMinTier()).thenReturn(LeagueTaskDifficulty.HARD);
    }

    @Test
    void notifyMastery() {
        // fire event
        notifier.onGameMessage("Congratulations, you've unlocked a new Melee Combat Mastery: Melee I.");

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_MASTERY)
                .text(
                    Template.builder()
                        .template(String.format("%s unlocked a new Combat Mastery: {{mastery}}.", PLAYER_NAME))
                        .replacement("{{mastery}}", Replacements.ofWiki("Melee I"))
                        .build()
                )
                .extra(new LeaguesMasteryNotificationData("Melee", 1))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyArea() {
        // update client mocks
        int tasksCompleted = 350;
        int totalPoints = 100 * 10 + 250 * 30;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_2)).thenReturn(4);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_3)).thenReturn(8);

        // fire event
        notifier.onGameMessage("Congratulations, you've unlocked a new area: Kandarin.");

        // verify notification
        String area = "Kandarin";
        int tasksUntilNextArea = THIRD_AREA_TASKS - tasksCompleted;
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_AREA)
                .text(
                    Template.builder()
                        .template(String.format("%s selected their second region: {{area}}.", PLAYER_NAME))
                        .replacement("{{area}}", Replacements.ofWiki(area, CURRENT_LEAGUE_NAME + " League/Areas/" + area))
                        .build()
                )
                .extra(new LeaguesAreaNotificationData(area, 2, tasksCompleted, tasksUntilNextArea))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyAreaKaramja() {
        // update client mocks
        int tasksCompleted = 80;
        int totalPoints = 80 * 10;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1)).thenReturn(2);

        // fire event
        notifier.onGameMessage("Congratulations, you've unlocked a new area: Karamja.");

        // verify notification
        String area = "Karamja";
        int tasksUntilNextArea = FIRST_AREA_TASKS - tasksCompleted;
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_AREA)
                .text(
                    Template.builder()
                        .template(String.format("%s selected their zeroth region: {{area}}.", PLAYER_NAME))
                        .replacement("{{area}}", Replacements.ofWiki(area, CURRENT_LEAGUE_NAME + " League/Areas/" + area))
                        .build()
                )
                .extra(new LeaguesAreaNotificationData(area, 0, tasksCompleted, tasksUntilNextArea))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyRelic() {
        // update client mocks
        int tasksCompleted = 2;
        int totalPoints = 2 * 10;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);

        // fire event
        notifier.onGameMessage("Congratulations, you've unlocked a new Relic: Endless Harvest.");

        // verify notification
        String relic = "Endless Harvest";
        int pointsUntilNextTier = LeagueRelicTier.TWO.getDefaultPoints() - totalPoints;
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_RELIC)
                .text(
                    Template.builder()
                        .template(String.format("%s unlocked a Tier 1 Relic: {{relic}}.", PLAYER_NAME))
                        .replacement("{{relic}}", Replacements.ofWiki(relic))
                        .build()
                )
                .extra(new LeaguesRelicNotificationData(relic, 1, 0, totalPoints, pointsUntilNextTier))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyTask() {
        // update client mocks
        int tasksCompleted = 201;
        int totalPoints = 200 * 10 + 80;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");

        // verify notification
        String taskName = "The Frozen Door";
        LeagueTaskDifficulty difficulty = LeagueTaskDifficulty.HARD;
        int tasksUntilNextArea = SECOND_AREA_TASKS - tasksCompleted;
        int pointsUntilNextRelic = LeagueRelicTier.FOUR.getDefaultPoints() - totalPoints;
        int pointsUntilNextTrophy = 4_000 - totalPoints;
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_TASK)
                .text(
                    Template.builder()
                        .template(String.format("%s completed a %s task: {{task}}.", PLAYER_NAME, "Hard"))
                        .replacement("{{task}}", Replacements.ofWiki(taskName, CURRENT_LEAGUE_NAME + " League/Tasks"))
                        .build()
                )
                .extra(new LeaguesTaskNotificationData(taskName, difficulty, difficulty.getPoints(), totalPoints, tasksCompleted, tasksUntilNextArea, pointsUntilNextRelic, pointsUntilNextTrophy, null))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyTaskToProfilesWithIndependentTierPolicies() {
        PigeonProfile clan = leaguesProfile("Clan", true, true, LeagueTaskDifficulty.HARD,
            "https://example.com/clan");
        PigeonProfile friends = leaguesProfile("Friends", true, true, LeagueTaskDifficulty.EASY,
            "https://example.com/friends");
        PigeonProfile elite = leaguesProfile("Elite", true, true, LeagueTaskDifficulty.ELITE,
            "https://example.com/elite");
        PigeonProfile tasksOff = leaguesProfile("Off", true, false, LeagueTaskDifficulty.EASY,
            "https://example.com/off");
        mockStoredProfiles(clan, friends, elite, tasksOff);
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(201);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(2_080);

        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), any());
        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
    }

    @Test
    void notifyTaskTrophyBronze() {
        // update client mocks
        int tasksCompleted = 113;
        int totalPoints = 100 * 10 + 80 * 13; // 2040 >= 2000
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");

        // verify notification
        String taskName = "The Frozen Door";
        LeagueTaskDifficulty difficulty = LeagueTaskDifficulty.HARD;
        int tasksUntilNextArea = FIRST_AREA_TASKS - tasksCompleted;
        int pointsUntilNextRelic = LeagueRelicTier.FOUR.getDefaultPoints() - totalPoints;
        int pointsUntilNextTrophy = 4_000 - totalPoints;
        String trophy = "Bronze";
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_TASK)
                .text(
                    Template.builder()
                        .template(String.format("%s completed a %s task, {{task}}, unlocking the {{trophy}} trophy!", PLAYER_NAME, "Hard"))
                        .replacement("{{task}}", Replacements.ofWiki(taskName, CURRENT_LEAGUE_NAME + " League/Tasks"))
                        .replacement("{{trophy}}", Replacements.ofWiki(trophy, CURRENT_LEAGUE_NAME + " " + trophy.toLowerCase() + " trophy"))
                        .build()
                )
                .extra(new LeaguesTaskNotificationData(taskName, difficulty, difficulty.getPoints(), totalPoints, tasksCompleted, tasksUntilNextArea, pointsUntilNextRelic, pointsUntilNextTrophy, trophy))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void notifyTaskTrophyIron() {
        // update mocks
        int tasksCompleted = 200;
        int totalPoints = 100 * 10 + 100 * 30; // 4000 >= 4000
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);
        when(config.leaguesTaskMinTier()).thenReturn(LeagueTaskDifficulty.EASY);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a medium task: Equip Amy's Saw.");

        // verify notification
        String taskName = "Equip Amy's Saw";
        LeagueTaskDifficulty difficulty = LeagueTaskDifficulty.MEDIUM;
        int tasksUntilNextArea = SECOND_AREA_TASKS - tasksCompleted;
        int pointsUntilNextRelic = LeagueRelicTier.FIVE.getDefaultPoints() - totalPoints;
        int pointsUntilNextTrophy = 10_000 - totalPoints;
        String trophy = "Iron";
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.LEAGUES_TASK)
                .text(
                    Template.builder()
                        .template(String.format("%s completed a %s task, {{task}}, unlocking the {{trophy}} trophy!", PLAYER_NAME, "Medium"))
                        .replacement("{{task}}", Replacements.ofWiki(taskName, CURRENT_LEAGUE_NAME + " League/Tasks"))
                        .replacement("{{trophy}}", Replacements.ofWiki(trophy, CURRENT_LEAGUE_NAME + " " + trophy.toLowerCase() + " trophy"))
                        .build()
                )
                .extra(new LeaguesTaskNotificationData(taskName, difficulty, difficulty.getPoints(), totalPoints, tasksCompleted, tasksUntilNextArea, pointsUntilNextRelic, pointsUntilNextTrophy, trophy))
                .playerName(PLAYER_NAME)
                .seasonalWorld(true)
                .build()
        );
    }

    @Test
    void ignoreTaskTier() {
        // update config mock
        when(config.leaguesTaskMinTier()).thenReturn(LeagueTaskDifficulty.ELITE);

        // update client mocks
        int tasksCompleted = 101;
        int totalPoints = 100 * 10 + 40;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnored() {
        // update config mocks
        when(config.leaguesAreaUnlock()).thenReturn(false);
        when(config.leaguesRelicUnlock()).thenReturn(false);
        when(config.leaguesTaskCompletion()).thenReturn(false);
        when(config.leaguesMasteryUnlock()).thenReturn(false);

        // update client mocks
        int tasksCompleted = 101;
        int totalPoints = 100 * 10 + 80;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_2)).thenReturn(4);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");
        notifier.onGameMessage("Congratulations, you've unlocked a new Relic: Animal Wrangler.");
        notifier.onGameMessage("Congratulations, you've unlocked a new area: Kandarin.");
        notifier.onGameMessage("Congratulations, you've unlocked a new Melee Combat Mastery: Melee I.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // update config mocks
        when(config.notifyLeagues()).thenReturn(false);

        // update client mocks
        int tasksCompleted = 101;
        int totalPoints = 100 * 10 + 80;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_2)).thenReturn(4);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard task: The Frozen Door.");
        notifier.onGameMessage("Congratulations, you've unlocked a new Relic: Animal Wrangler.");
        notifier.onGameMessage("Congratulations, you've unlocked a new area: Kandarin.");
        notifier.onGameMessage("Congratulations, you've unlocked a new Melee Combat Mastery: Melee I.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void ignoreIrrelevant() {
        // update client mocks
        int tasksCompleted = 101;
        int totalPoints = 100 * 10 + 80;
        when(client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED)).thenReturn(tasksCompleted);
        when(client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED)).thenReturn(totalPoints);

        // fire event
        notifier.onGameMessage("Congratulations, you've completed a hard combat task: Ready to Pounce.");

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private PigeonProfile leaguesProfile(String name, boolean enabled, boolean tasks,
                                         LeagueTaskDifficulty minimum, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID()).name(name).enabled(enabled)
            .settings(Map.of(
                "notifyLeagues", new JsonPrimitive(true),
                "leaguesSendImage", new JsonPrimitive(false),
                "leaguesAreaUnlock", new JsonPrimitive(false),
                "leaguesRelicUnlock", new JsonPrimitive(false),
                "leaguesTaskCompletion", new JsonPrimitive(tasks),
                "leaguesMasteryUnlock", new JsonPrimitive(false),
                "leaguesTaskMinTier", new JsonPrimitive(minimum.name())))
            .webhooks(new ProfileWebhooks(List.of(webhook), Map.of())).build();
    }

    private void mockStoredProfiles(PigeonProfile... profiles) {
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
