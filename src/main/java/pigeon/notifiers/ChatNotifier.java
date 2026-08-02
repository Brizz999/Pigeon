package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.domain.ChatNotificationType;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.ChatNotificationData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.Utils;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static pigeon.domain.ChatNotificationType.*;

@Singleton
public class ChatNotifier extends BaseNotifier {
    public static final String PATTERNS_CONFIG_KEY = "chatPatterns";

    @Inject
    private ClientThread clientThread;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    private final Collection<Pattern> regexps = new ArrayList<>();
    private volatile boolean dirty;

    @Override
    public boolean isEnabled() {
        return config.notifyChat() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.chatWebhook();
    }

    public void init() {
        this.dirty = true;
    }

    public void reset() {
        this.dirty = true;
        clientThread.invoke(() -> {
            if (dirty) {
                regexps.clear();
            }
        });
    }

    public void onConfig(String key) {
        if (PATTERNS_CONFIG_KEY.equals(key)) {
            this.dirty = true;
        }
    }

    public void onTick() {
        if (this.dirty) {
            var username = Utils.getPlayerName(client);
            if (username == null || username.isEmpty()) {
                return;
            }
            this.dirty = false;
            this.loadPatterns(username);
        }
    }

    public void onMessage(@NotNull ChatMessageType messageType, @Nullable String source, @NotNull String message) {
        ChatNotificationType type = ChatNotificationType.MAPPINGS.get(messageType);
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = type != null && (profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(profile -> isProfileEnabled(profile)
                && profile.chatMessageTypes().contains(type))
            : config.chatMessageTypes().contains(type) && isEnabled());
        if (enabled) {
            clientThread.invoke(() -> {
                if (!profiles.isProfilesConfigured() && dirty)
                    return false; // try later

                String cleanSource = source != null ? Text.sanitize(source) : null;
                this.handleNotify(profiles, type, messageType, cleanSource, message);
                return true;
            });
        }
    }

    public void onCommand(CommandExecuted event) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(profile -> isProfileEnabled(profile)
                && profile.chatMessageTypes().contains(COMMAND))
            : config.chatMessageTypes().contains(COMMAND) && isEnabled();
        if (enabled) {
            String fullMessage = join(event);
            clientThread.invoke(() -> {
                if (!profiles.isProfilesConfigured() && dirty)
                    return false; // try later

                this.handleNotify(profiles, COMMAND, ChatMessageType.UNKNOWN, "CommandExecuted", fullMessage);
                return true;
            });
        }
    }

    public void onNotification(NotificationFired event) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean gameMessage = event.getNotification().isGameMessage()
            && client.getGameState() == GameState.LOGGED_IN;
        if (!profiles.isProfilesConfigured() && gameMessage && config.chatMessageTypes().contains(GAME)) return;
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(profile -> isProfileEnabled(profile)
                && profile.chatMessageTypes().contains(RUNELITE)
                && !(gameMessage && profile.chatMessageTypes().contains(GAME)))
            : config.chatMessageTypes().contains(RUNELITE) && isEnabled();
        if (enabled) {
            clientThread.invoke(() -> {
                if (!profiles.isProfilesConfigured() && dirty)
                    return false; // try later

                this.handleNotify(profiles, RUNELITE, ChatMessageType.UNKNOWN,
                    "NotificationFired", event.getMessage(), profile ->
                        !(gameMessage && profile.chatMessageTypes().contains(GAME)));
                return true;
            });
        }
    }

    private void handleNotify(ProfileRuntimeSnapshot profiles, ChatNotificationType notificationType,
                              ChatMessageType type, String source, String message) {
        handleNotify(profiles, notificationType, type, source, message, profile -> true);
    }

    private void handleNotify(ProfileRuntimeSnapshot profiles, ChatNotificationType notificationType,
                              ChatMessageType type, String source, String message,
                              java.util.function.Predicate<PigeonProfileConfig> extraFilter) {
        if (profiles.isProfilesConfigured()) {
            String username = Utils.getPlayerName(client);
            List<PigeonProfileConfig> recipients = profiles.getEnabledProfiles().stream()
                .filter(this::isProfileEnabled)
                .filter(profile -> profile.chatMessageTypes().contains(notificationType))
                .filter(extraFilter)
                .filter(profile -> hasMatch(profile.chatPatterns(), username, message))
                .collect(Collectors.toList());
            if (recipients.isEmpty()) return;
            var clanTitle = getClanTitle(type, source, message);
            ChatNotificationData extra = new ChatNotificationData(type, source, clanTitle, message);
            NotificationBody<?> outbound = null;
            for (PigeonProfileConfig profile : recipients) {
                NotificationBody<?> body = createBody(profile, notificationType, source, message, username, extra);
                if (deliver(profile, profile.chatWebhook(), profile.chatSendImage(), body)
                    && outbound == null) outbound = body;
            }
            if (outbound != null) publishPluginMessage(outbound);
            return;
        }
        if (!hasMatch(message)) return;
        var clanTitle = getClanTitle(type, source, message);
        String playerName = Utils.getPlayerName(client);
        createMessage(config.chatSendImage(), createBody(config, notificationType, source, message,
            playerName, new ChatNotificationData(type, source, clanTitle, message)));
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, ChatNotificationType notificationType,
                                            String source, String message, String playerName,
                                            ChatNotificationData extra) {
        Template template = Template.builder()
            .template(deliveryConfig.chatNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(playerName))
            .replacement("%MESSAGE%", Replacements.ofText(message))
            .replacement("%SENDER%", Replacements.ofText(getSender(notificationType, source)))
            .build();
        return NotificationBody.builder()
            .text(template)
            .type(NotificationType.CHAT)
            .extra(extra)
            .playerName(playerName)
            .build();
    }

    private boolean hasMatch(String chatMessage) {
        for (Pattern pattern : regexps) {
            if (pattern.matcher(chatMessage).find())
                return true;
        }
        return false;
    }

    private boolean hasMatch(String patterns, String username, String chatMessage) {
        return patterns.lines().map(String::trim).filter(s -> !s.isEmpty())
            .map(s -> s.replace("%USERNAME%", username))
            .map(Utils::regexify).filter(Objects::nonNull)
            .anyMatch(pattern -> pattern.matcher(chatMessage).find());
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyChat() && profileEligibilityService.isEligible(profile);
    }

    private void loadPatterns(String username) {
        regexps.clear();
        regexps.addAll(
            config.chatPatterns().lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replace("%USERNAME%", username))
                .map(Utils::regexify)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
    }

    @Nullable
    private ClanTitle getClanTitle(@NotNull ChatMessageType type, @Nullable String source, @NotNull String message) {
        if (type == ChatMessageType.CLAN_MESSAGE && message.endsWith(" has joined.")) {
            String name = message.substring(0, message.length() - " has joined.".length());
            var title = getClanTitle(ChatMessageType.CLAN_CHAT, name);
            return title != null ? title : getClanTitle(ChatMessageType.CLAN_GUEST_CHAT, name);
        }
        return getClanTitle(type, source);
    }

    @Nullable
    private ClanTitle getClanTitle(@NotNull ChatMessageType type, @Nullable String name) {
        if (name == null) return null;

        ClanChannel channel;
        ClanSettings settings;
        if (type == ChatMessageType.CLAN_CHAT) {
            channel = client.getClanChannel();
            settings = client.getClanSettings();
        } else if (type == ChatMessageType.CLAN_GUEST_CHAT) {
            channel = client.getGuestClanChannel();
            settings = client.getGuestClanSettings();
        } else if (type == ChatMessageType.CLAN_GIM_CHAT) {
            channel = client.getClanChannel(ClanID.GROUP_IRONMAN);
            settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
        } else {
            channel = null;
            settings = null;
        }

        ClanChannelMember member;
        if (channel == null || settings == null || (member = channel.findMember(name)) == null) {
            return null;
        }
        return settings.titleForRank(member.getRank());
    }

    private static String getSender(ChatNotificationType type, String source) {
		if (source == null || source.isEmpty() || type == COMMAND || type == RUNELITE) {
			return "[" + type + "]";
		}
		return source;
    }

    private static String join(CommandExecuted event) {
        StringBuilder sb = new StringBuilder();
        sb.append("::").append(event.getCommand());

        String[] args = event.getArguments();
        if (args != null) {
            for (String arg : args) {
                sb.append(' ').append(arg);
            }
        }
        return sb.toString();
    }
}
