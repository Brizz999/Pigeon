package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.QuestNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pigeon.util.QuestUtils.parseQuestWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    QuestNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyQuest()).thenReturn(true);
        when(config.questSendImage()).thenReturn(false);
        when(config.questNotifyMessage()).thenReturn("%USERNAME% has completed: %QUEST%");

    }

    @Test
    void testNotify() {
        // init client mocks
        when(client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT)).thenReturn(22);
        when(client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT)).thenReturn(156);
        when(client.getVarpValue(VarPlayerID.QP)).thenReturn(44);
        when(client.getVarbitValue(VarbitID.QP_MAX)).thenReturn(293);

        // mock widget
        Widget questWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Questscroll.QUEST_TITLE)).thenReturn(questWidget);
        when(questWidget.getText()).thenReturn("You have completed the Dragon Slayer I quest!");

        // send event
        plugin.onWidgetLoaded(event(InterfaceID.QUESTSCROLL));

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(PLAYER_NAME + " has completed: {{quest}}")
                        .replacement("{{quest}}", Replacements.ofWiki("Dragon Slayer I"))
                        .build()
                )
                .extra(new QuestNotificationData("Dragon Slayer I", 22, 156, 44, 293))
                .type(NotificationType.QUEST)
                .build()
        );
    }

    @Test
    void testNotifyTwoEnabledProfilesIndependently() {
        when(client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT)).thenReturn(22);
        when(client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT)).thenReturn(156);
        when(client.getVarpValue(VarPlayerID.QP)).thenReturn(44);
        when(client.getVarbitValue(VarbitID.QP_MAX)).thenReturn(293);

        PigeonProfile clan = profile("Clan", true, "Clan: %QUEST%", "https://example.com/clan");
        PigeonProfile friends = profile("Friends", true, "Friends: %QUEST%", "https://example.com/friends");
        PigeonProfile disabled = profile("Disabled", false, "Disabled: %QUEST%", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, disabled);

        Widget questWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Questscroll.QUEST_TITLE)).thenReturn(questWidget);
        when(questWidget.getText()).thenReturn("You have completed the Dragon Slayer I quest!");

        plugin.onWidgetLoaded(event(InterfaceID.QUESTSCROLL));

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(),
            urls.capture(),
            anyBoolean(),
            bodies.capture()
        );

        assertEquals(List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        assertEquals("Clan: Dragon Slayer I", bodies.getAllValues().get(0).getText().evaluate(false));
        assertEquals("Friends: Dragon Slayer I", bodies.getAllValues().get(1).getText().evaluate(false));
        assertEquals(List.of("Clan", "Friends"), List.of(
            ((pigeon.profiles.PigeonProfileConfig) configs.getAllValues().get(0)).getProfile().getName(),
            ((pigeon.profiles.PigeonProfileConfig) configs.getAllValues().get(1)).getProfile().getName()
        ));
    }

    @Test
    void testIgnore() {
        // send unrelated event
        plugin.onWidgetLoaded(event(-1));

        // verify no message
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // disable notifier
        when(config.notifyQuest()).thenReturn(false);

        // mock widget
        Widget questWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Questscroll.QUEST_TITLE)).thenReturn(questWidget);
        when(questWidget.getText()).thenReturn("You have completed the Dragon Slayer quest!");

        // send event
        plugin.onWidgetLoaded(event(InterfaceID.QUESTSCROLL));

        // verify no message
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void parseWidgets() {
        assertEquals("Recipe for Disaster - Another Cook's Quest", parseQuestWidget("You have completed Another Cook's Quest!"));
        assertEquals("Recipe for Disaster - Another Cook's Quest", parseQuestWidget("You have assisted the Lumbridge Cook... again!"));
        assertEquals("Recipe for Disaster - Goblin Generals", parseQuestWidget("You have freed the Goblin Generals!"));
        assertEquals("Recipe for Disaster - Sir Amik Varze", parseQuestWidget("You have freed Sir Amik Varze!"));
        assertEquals("Recipe for Disaster - Skrach Uglogwee", parseQuestWidget("You have freed Skrach 'Bone Crusher' Uglogwee!"));
        assertEquals("Recipe for Disaster", parseQuestWidget("You have completed Recipe for Disaster!"));

        assertEquals("Doric's Quest", parseQuestWidget("You have completed Doric's Quest!"));
        assertEquals("Heroes Quest", parseQuestWidget("You have completed the Heroes' Quest!"));

        assertEquals("Dragon Slayer II", parseQuestWidget("You have completed Dragon Slayer II!"));
        assertEquals("Rag and Bone Man II", parseQuestWidget("You have completed Rag and Bone Man II!"));
        assertEquals("Fairytale II - Cure a Queen", parseQuestWidget("You have completed Fairytale II - Cure a Queen!"));

        assertNull(parseQuestWidget("You have... kind of... completed Hazeel Cult!"));
        assertEquals("Hazeel Cult", parseQuestWidget("You have completed Hazeel Cult!"));
    }

    private PigeonProfile profile(String name, boolean enabled, String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "questEnabled", new JsonPrimitive(true),
                "questSendImage", new JsonPrimitive(false),
                "questNotifMessage", new JsonPrimitive(message)
            ))
            .webhooks(new ProfileWebhooks(List.of(webhook), Map.of()))
            .build();
    }

    private void mockStoredProfiles(PigeonProfile... profiles) {
        profileRuntimeService.invalidate();
        List<UUID> ids = java.util.Arrays.stream(profiles)
            .map(PigeonProfile::getId)
            .collect(java.util.stream.Collectors.toList());
        when(configManager.getConfiguration(ConfigProfileRepository.CONFIG_GROUP, ConfigProfileRepository.INDEX_KEY))
            .thenReturn(gson.toJson(ids));
        for (PigeonProfile profile : profiles) {
            when(configManager.getConfiguration(
                ConfigProfileRepository.CONFIG_GROUP,
                "profile_" + profile.getId()
            )).thenReturn(gson.toJson(profile));
        }
    }

    private static WidgetLoaded event(int id) {
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(id);
        return event;
    }

}
