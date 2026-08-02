package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.ClueTier;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.ClueNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClueNotifierTest extends MockedNotifierTest {

    private static final int RUBY_PRICE = 900;
    private static final int TUNA_PRICE = 100;

    @Bind
    @InjectMocks
    ClueNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyClue()).thenReturn(true);
        when(config.clueSendImage()).thenReturn(false);
        when(config.clueShowItems()).thenReturn(false);
        when(config.clueMinTier()).thenReturn(ClueTier.MEDIUM);
        when(config.clueMinValue()).thenReturn(500);
        when(config.clueNotifyMessage()).thenReturn("%USERNAME% has completed a %CLUE% clue, for a total of %COUNT%. They obtained: %LOOT%");

        // init item mocks
        mockItem(ItemID.RUBY, RUBY_PRICE, "Ruby");
        mockItem(ItemID.TUNA, TUNA_PRICE, "Tuna");
    }

    @Test
    void testNotify() {
        // fire chat event
        notifier.onChatMessage("You have completed 1312 medium Treasure Trails.");

        // mock widgets
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);

        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.RUBY);

        Widget[] children = { child };
        when(widget.getChildren()).thenReturn(children);

        // fire widget event
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(String.format("%s has completed a {{tier}} clue, for a total of %d. They obtained: 1 x {{ruby}} (%d)", PLAYER_NAME, 1312, RUBY_PRICE))
                        .replacement("{{tier}}", Replacements.ofWiki("medium", "Clue scroll (medium)"))
                        .replacement("{{ruby}}", Replacements.ofWiki("Ruby"))
                        .build()
                )
                .extra(new ClueNotificationData("medium", 1312, Collections.singletonList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby"))))
                .type(NotificationType.CLUE)
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneClueReward() {
        PigeonProfile clan = clueProfile(
            "Clan", true, ClueTier.EASY, 500,
            "Clan: %CLUE% %LOOT%", "https://example.com/clan");
        PigeonProfile friends = clueProfile(
            "Friends", true, ClueTier.MEDIUM, 900,
            "Friends: %CLUE% %LOOT%", "https://example.com/friends");
        PigeonProfile hard = clueProfile(
            "Hard", true, ClueTier.HARD, 0,
            "Hard", "https://example.com/hard");
        PigeonProfile disabled = clueProfile(
            "Disabled", false, ClueTier.BEGINNER, 0,
            "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, hard, disabled);

        notifier.onChatMessage("You have completed 1312 medium Treasure Trails.");
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);
        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.RUBY);
        when(widget.getChildren()).thenReturn(new Widget[] { child });
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: medium 1 x Ruby (900)", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: medium 1 x Ruby (900)", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testNotifyFirst() {
        // update config mock
        when(config.clueMinTier()).thenReturn(ClueTier.EASY);

        // fire chat event
        notifier.onChatMessage("You have completed 1 easy Treasure Trail.");

        // mock widgets
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);

        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.RUBY);

        Widget[] children = { child };
        when(widget.getChildren()).thenReturn(children);

        // fire widget event
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(String.format("%s has completed a {{tier}} clue, for a total of %d. They obtained: 1 x {{ruby}} (%d)", PLAYER_NAME, 1, RUBY_PRICE))
                        .replacement("{{tier}}", Replacements.ofWiki("easy", "Clue scroll (easy)"))
                        .replacement("{{ruby}}", Replacements.ofWiki("Ruby"))
                        .build()
                )
                .extra(new ClueNotificationData("easy", 1, Collections.singletonList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby"))))
                .type(NotificationType.CLUE)
                .build()
        );
    }

    @Test
    void testIgnoreTier() {
        // fire chat event
        notifier.onChatMessage("You have completed 1312 beginner Treasure Trails.");

        // mock widgets
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);

        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.RUBY);

        Widget[] children = { child };
        when(widget.getChildren()).thenReturn(children);

        // fire widget event
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        // ensure no notification was fired
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreLoot() {
        // fire chat event
        notifier.onChatMessage("You have completed 1337 medium Treasure Trails.");

        // mock widgets
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);

        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.TUNA);

        Widget[] children = { child };
        when(widget.getChildren()).thenReturn(children);

        // fire widget event
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        // ensure no notification was fired
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // disable notifier
        when(config.notifyClue()).thenReturn(false);

        // fire chat event
        notifier.onChatMessage("You have completed 1312 medium Treasure Trails.");

        // mock widgets
        Widget widget = mock(Widget.class);
        when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(widget);

        Widget child = mock(Widget.class);
        when(child.getItemQuantity()).thenReturn(1);
        when(child.getItemId()).thenReturn(ItemID.RUBY);

        Widget[] children = { child };
        when(widget.getChildren()).thenReturn(children);

        // fire widget event
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
        plugin.onWidgetLoaded(event);

        // ensure no notification was fired
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private PigeonProfile clueProfile(String name, boolean enabled, ClueTier minimum, int minValue,
                                      String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "clueEnabled", new JsonPrimitive(true),
                "clueSendImage", new JsonPrimitive(false),
                "clueShowItems", new JsonPrimitive(false),
                "clueMinTier", new JsonPrimitive(minimum.name()),
                "clueMinValue", new JsonPrimitive(minValue),
                "clueNotifMessage", new JsonPrimitive(message)
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
