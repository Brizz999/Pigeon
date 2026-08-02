package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.AchievementDiary;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.DiaryNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.stream.IntStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("MagicConstant")
class DiaryNotifierTest extends MockedNotifierTest {

    private static final int COMPLETED_TASKS = 469;
    private static final int TOTAL_TASKS = 492;

    private static final int KARAMJA_TASKS_FROM_EASY_TO_HARD = 10 + 19 + 10;
    private static final int KARAMJA_TOTAL_TASKS = KARAMJA_TASKS_FROM_EASY_TO_HARD + 5;

    @Bind
    @InjectMocks
    DiaryNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyAchievementDiary()).thenReturn(true);
        when(config.diarySendImage()).thenReturn(false);
        when(config.minDiaryDifficulty()).thenReturn(AchievementDiary.Difficulty.MEDIUM);
        when(config.diaryNotifyMessage()).thenReturn("%USERNAME% has completed the %DIFFICULTY% %AREA% Diary, for a total of %TOTAL%");

        // init client mocks
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(
            new int[] { COMPLETED_TASKS }, // first COMPLETED_TASKS_SCRIPT_ID is run
            new int[] { TOTAL_TASKS }, // then TOTAL_TASKS_SCRIPT_ID is run
            new int[] { KARAMJA_TASKS_FROM_EASY_TO_HARD }, // then COMPLETED_AREA_TASKS_SCRIPT_ID
            new int[] { KARAMJA_TOTAL_TASKS } // then TOTAL_AREA_TASKS_SCRIPT_ID
        );
    }

    @Test
    void testNotifyFirst() {
        // initially 0 diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        when(config.minDiaryDifficulty()).thenReturn(AchievementDiary.Difficulty.EASY);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        int tasksCompleted = 11;
        int totalAreaTasks = 11 + 12 + 10 + 6;
        when(client.getIntStack()).thenReturn(
            new int[] { tasksCompleted }, // first COMPLETED_TASKS_SCRIPT_ID is run
            new int[] { TOTAL_TASKS }, // then TOTAL_TASKS_SCRIPT_ID is run
            new int[] { tasksCompleted }, // then COMPLETED_AREA_TASKS_SCRIPT_ID is run
            new int[] { totalAreaTasks } // then TOTAL_TASKS_SCRIPT_ID is run
        );
        int id = VarbitID.DESERT_DIARY_EASY_COMPLETE;
        plugin.onVarbitChanged(event(id, 1));

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(AchievementDiary.Difficulty.EASY, "Desert", 1))
                .extra(new DiaryNotificationData("Desert", AchievementDiary.Difficulty.EASY, 1, tasksCompleted, TOTAL_TASKS, tasksCompleted, totalAreaTasks))
                .type(NotificationType.ACHIEVEMENT_DIARY)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneDiaryCompletion() {
        PigeonProfile clan = diaryProfile(
            "Clan", true, AchievementDiary.Difficulty.EASY,
            "Clan: %DIFFICULTY% %AREA%", "https://example.com/clan");
        PigeonProfile friends = diaryProfile(
            "Friends", true, AchievementDiary.Difficulty.HARD,
            "Friends: %DIFFICULTY% %AREA%", "https://example.com/friends");
        PigeonProfile eliteOnly = diaryProfile(
            "Elite", true, AchievementDiary.Difficulty.ELITE,
            "Elite", "https://example.com/elite");
        PigeonProfile disabled = diaryProfile(
            "Disabled", false, AchievementDiary.Difficulty.EASY,
            "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, eliteOnly, disabled);

        when(client.getVarbitValue(anyInt())).thenReturn(1);
        IntStream.range(0, 16).forEach(i -> notifier.onTick());
        notifier.onMessageBox("Congratulations! You have completed all of the hard tasks in the Karamja area. Speak to Pirate Jackie the Fruit to claim your reward.");

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: Hard Karamja", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: Hard Karamja", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testNotifyKaramja() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);
        int total = AchievementDiary.DIARIES.size() - 3;

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary started
        int id = VarbitID.ATJUN_HARD_DONE;
        plugin.onVarbitChanged(event(id, 2));

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(AchievementDiary.Difficulty.HARD, "Karamja", total + 1))
                .extra(new DiaryNotificationData("Karamja", AchievementDiary.Difficulty.HARD, total + 1, COMPLETED_TASKS, TOTAL_TASKS, KARAMJA_TASKS_FROM_EASY_TO_HARD, KARAMJA_TOTAL_TASKS))
                .type(NotificationType.ACHIEVEMENT_DIARY)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyKaramjaMessageBox() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);
        int total = AchievementDiary.DIARIES.size() - 3;

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        notifier.onMessageBox("Congratulations! You have completed all of the hard tasks in the Karamja area. Speak to Pirate Jackie the Fruit to claim your reward.");

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(AchievementDiary.Difficulty.HARD, "Karamja", total + 1))
                .extra(new DiaryNotificationData("Karamja", AchievementDiary.Difficulty.HARD, total + 1, COMPLETED_TASKS, TOTAL_TASKS, KARAMJA_TASKS_FROM_EASY_TO_HARD, KARAMJA_TOTAL_TASKS))
                .type(NotificationType.ACHIEVEMENT_DIARY)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testDontNotifyKaramjaMessageBox() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        notifier.onMessageBox("Congratulations! You have completed all of the easy tasks in the Karamja area. Speak to Pirate Jackie the Fruit to claim your reward.");

        // verify no notification message
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testNotifyWesternMessageBox() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);
        when(client.getVarbitValue(VarbitID.WESTERN_DIARY_HARD_COMPLETE)).thenReturn(0);
        int total = AchievementDiary.DIARIES.size() - 3 - 1;

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        int westernTasksFromEasyToHard = 11 + 13 + 13;
        int westernTasksTotal = westernTasksFromEasyToHard + 7;
        when(client.getIntStack()).thenReturn(
            new int[] { COMPLETED_TASKS }, // first COMPLETED_TASKS_SCRIPT_ID is run
            new int[] { TOTAL_TASKS }, // then TOTAL_TASKS_SCRIPT_ID is run
            new int[] { westernTasksFromEasyToHard }, // then COMPLETED_AREA_TASKS_SCRIPT_ID
            new int[] { westernTasksTotal } // then TOTAL_AREA_TASKS_SCRIPT_ID
        );
        notifier.onMessageBox("Congratulations! You have completed all of the hard tasks in the Western Province area. Speak to the Elder Gnome child at the Gnome Stronghold to claim your reward.");

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(AchievementDiary.Difficulty.HARD, "Western Provinces", total + 1))
                .extra(new DiaryNotificationData("Western Provinces", AchievementDiary.Difficulty.HARD, total + 1, COMPLETED_TASKS, TOTAL_TASKS, westernTasksFromEasyToHard, westernTasksTotal))
                .type(NotificationType.ACHIEVEMENT_DIARY)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyCooldown() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);
        int total = AchievementDiary.DIARIES.size() - 3;

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        int id = VarbitID.ATJUN_HARD_DONE;
        plugin.onVarbitChanged(event(id, 2));

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(AchievementDiary.Difficulty.HARD, "Karamja", total + 1))
                .extra(new DiaryNotificationData("Karamja", AchievementDiary.Difficulty.HARD, total + 1, COMPLETED_TASKS, TOTAL_TASKS, KARAMJA_TASKS_FROM_EASY_TO_HARD, KARAMJA_TOTAL_TASKS))
                .type(NotificationType.ACHIEVEMENT_DIARY)
                .playerName(PLAYER_NAME)
                .build()
        );

        // trigger message box
        notifier.onMessageBox("Congratulations! You have completed all of the hard tasks in the Karamja area. Speak to Pirate Jackie the Fruit to claim your reward.");

        // ensure no notification
        Mockito.verifyNoMoreInteractions(messageHandler);
    }

    @Test
    void testIgnoreKaramja() {
        // initially 0 diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(0);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary started
        int id = VarbitID.ATJUN_HARD_DONE;
        plugin.onVarbitChanged(event(id, 1));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreCompleted() {
        // initially many diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(1);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger varbit event
        int id = VarbitID.DESERT_DIARY_ELITE_COMPLETE;
        plugin.onVarbitChanged(event(id, 1));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreUninitialized() {
        // trigger varbit event
        int id = VarbitID.DESERT_DIARY_ELITE_COMPLETE;
        plugin.onVarbitChanged(event(id, 1));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreDifficulty() {
        // initially 0 diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(0);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger varbit event
        int id = VarbitID.FALADOR_DIARY_EASY_COMPLETE;
        plugin.onVarbitChanged(event(id, 1));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // disable notifier
        when(config.notifyAchievementDiary()).thenReturn(false);

        // initially 0 diary completions
        when(client.getVarbitValue(anyInt())).thenReturn(0);

        // perform enough ticks to trigger diary initialization
        IntStream.range(0, 16).forEach(i -> notifier.onTick());

        // trigger diary completion
        int id = VarbitID.DESERT_DIARY_ELITE_COMPLETE;
        plugin.onVarbitChanged(event(id, 1));

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private static VarbitChanged event(int id, int value) {
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(id);
        event.setValue(value);
        return event;
    }

    private static Template buildTemplate(AchievementDiary.Difficulty difficulty, String area, int total) {
        return Template.builder()
            .template(String.format("%s has completed the %s {{area}} Diary, for a total of %d", PLAYER_NAME, difficulty, total))
            .replacement("{{area}}", Replacements.ofWiki(area, area + " Diary"))
            .build();
    }

    private PigeonProfile diaryProfile(String name, boolean enabled,
                                       AchievementDiary.Difficulty minimum,
                                       String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "diaryEnabled", new JsonPrimitive(true),
                "diarySendImage", new JsonPrimitive(false),
                "diaryMinDifficulty", new JsonPrimitive(minimum.name()),
                "diaryMessage", new JsonPrimitive(message)
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
