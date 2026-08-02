package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.ChatNotificationType;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.ChatNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.ChatMessageType;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.Notification;
import net.runelite.client.events.NotificationFired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.awt.TrayIcon;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class ChatNotifierTest extends MockedNotifierTest {

    @Bind
    @InjectMocks
    ChatNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // config mocks
        when(config.notifyChat()).thenReturn(true);
        when(config.chatMessageTypes()).thenReturn(EnumSet.of(ChatNotificationType.GAME, ChatNotificationType.COMMAND, ChatNotificationType.RUNELITE, ChatNotificationType.CLAN));
        when(config.chatNotifyMessage()).thenReturn("%SENDER%: %USERNAME% received a chat message:\n\n```\n%MESSAGE%\n```");
        setPatterns("You will be logged out in approximately 10 minutes.*\n" +
            "You will be logged out in approximately 5 minutes.*\n" +
            "Dragon impling is in the area\n" +
            "::TriggerDink\n" +
            "%USERNAME% has deposited * coin* into the coffer.\n" +
            "* has joined.");
    }

    @Test
    void testNotify() {
        // fire event
        String message = "You will be logged out in approximately 10 minutes.";
        notifier.onMessage(ChatMessageType.GAMEMESSAGE, null, message);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("[Game Engine]: " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.GAMEMESSAGE, null, null, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testProfilesUseIndependentChatPatternsAndTypes() {
        PigeonProfile clan = chatProfile("Clan", "*logged out*", ChatNotificationType.GAME,
            "Clan %MESSAGE%", "https://example.com/clan");
        PigeonProfile friends = chatProfile("Friends", "You will be logged out*", ChatNotificationType.GAME,
            "Friends %SENDER%", "https://example.com/friends");
        PigeonProfile noMatch = chatProfile("No match", "Dragon impling*", ChatNotificationType.GAME,
            "No", "https://example.com/no");
        PigeonProfile wrongType = chatProfile("Wrong type", "*", ChatNotificationType.PUBLIC,
            "Wrong", "https://example.com/wrong");
        mockStoredProfiles(clan, friends, noMatch, wrongType);
        String message = "You will be logged out in approximately 10 minutes.";

        notifier.onMessage(ChatMessageType.GAMEMESSAGE, null, message);

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals("Clan " + message, bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals("Friends [Game Engine]", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testNotifyUsername() {
        // fire event
        String message = PLAYER_NAME + " has deposited one coin into the coffer.";
        notifier.onMessage(ChatMessageType.GAMEMESSAGE, null, message);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("[Game Engine]: " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.GAMEMESSAGE, null, null, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyCommand() {
        // fire event
        String message = "::TriggerDink";
        notifier.onCommand(new CommandExecuted(message.substring(2), new String[0]));

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("[Client Commands]: " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.UNKNOWN, "CommandExecuted", null, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyTray() {
        // fire event
        String message = "Dragon impling is in the area";
        notifier.onNotification(new NotificationFired(Notification.ON, message, TrayIcon.MessageType.INFO));

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("[RuneLite Notifications]: " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.UNKNOWN, "NotificationFired", null, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyClan() {
        // update mocks
        var channel = mock(ClanChannel.class);
        var settings = mock(ClanSettings.class);
        when(client.getClanChannel()).thenReturn(channel);
        when(client.getClanSettings()).thenReturn(settings);

        var rank = ClanRank.OWNER;
        var title = new ClanTitle(rank.getRank(), "Queen");
        when(settings.titleForRank(rank)).thenReturn(title);

        var member = mock(ClanChannelMember.class);
        when(channel.findMember("Poki")).thenReturn(member);
        when(member.getRank()).thenReturn(rank);

        // fire event
        String message = "Poki has joined.";
        notifier.onMessage(ChatMessageType.CLAN_MESSAGE, "", message);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("[Clan Notifications]: " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.CLAN_MESSAGE, "", title, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyPlayer() {
        // update mocks
        when(config.chatMessageTypes()).thenReturn(EnumSet.of(ChatNotificationType.PUBLIC));
        setPatterns("*");

        // fire event
        String source = "forsen";
        String message = "Basedge";
        notifier.onMessage(ChatMessageType.PUBLICCHAT, source, message);

        // verify notification message
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(source + ": " + PLAYER_NAME + " received a chat message:\n\n```\n" + message + "\n```")
                        .build()
                )
                .extra(new ChatNotificationData(ChatMessageType.PUBLICCHAT, source, null, message))
                .type(NotificationType.CHAT)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testIgnore() {
        // fire event
        notifier.onMessage(ChatMessageType.GAMEMESSAGE, null, "You will be logged out in approximately 30 minutes.");
        notifier.onMessage(ChatMessageType.PUBLICCHAT, null, "You will be logged out in approximately 10 minutes.");
        notifier.onMessage(ChatMessageType.TRADE, null, "You will be logged out in approximately 10 minutes.");
        notifier.onMessage(ChatMessageType.PRIVATECHAT, null, "You will be logged out in approximately 10 minutes.");
        notifier.onCommand(new CommandExecuted("You", "will be logged out in approximately 10 minutes.".split(" ")));
        notifier.onCommand(new CommandExecuted("DontTriggerDink", new String[0]));
        notifier.onNotification(new NotificationFired(Notification.ON, "TriggerDink", TrayIcon.MessageType.INFO));

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // update config mock
        when(config.notifyChat()).thenReturn(false);

        // fire event
        notifier.onMessage(ChatMessageType.GAMEMESSAGE, null, "You will be logged out in approximately 10 minutes.");
        notifier.onCommand(new CommandExecuted("TriggerDink", new String[0]));

        // ensure no notification occurred
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private void setPatterns(String configValue) {
        when(config.chatPatterns()).thenReturn(configValue);
        notifier.onConfig(ChatNotifier.PATTERNS_CONFIG_KEY);
        notifier.onTick();
    }

    private PigeonProfile chatProfile(String name, String patterns, ChatNotificationType type,
                                      String message, String webhook) {
        return PigeonProfile.builder().schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID()).name(name).enabled(true)
            .settings(Map.of(
                "notifyChat", new JsonPrimitive(true),
                "chatSendImage", new JsonPrimitive(false),
                "chatMessageTypes", gson.toJsonTree(EnumSet.of(type)),
                "chatPatterns", new JsonPrimitive(patterns),
                "chatNotifyMessage", new JsonPrimitive(message)))
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
