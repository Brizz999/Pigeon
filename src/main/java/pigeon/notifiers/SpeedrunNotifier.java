package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.SpeedrunNotificationData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.QuestUtils;
import pigeon.util.TimeUtils;
import pigeon.util.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.time.Duration;
import javax.inject.Inject;

@Slf4j
public class SpeedrunNotifier extends BaseNotifier {
    private boolean isPersonalBest = false;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        // intentionally doesn't call super as WorldUtils.isIgnoredWorld includes speedrunning
        return config.notifySpeedrun() && accountTracker.hasValidState();
    }

    @Override
    protected String getWebhookUrl() {
        return config.speedrunWebhook();
    }

    public void reset() {
        isPersonalBest = false;
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (event.getGroupId() == InterfaceID.QUESTSCROLL_SPEEDRUN && enabled) {
            Widget questName = client.getWidget(InterfaceID.QuestscrollSpeedrun.QUEST_TITLE);
            Widget duration = client.getWidget(InterfaceID.QuestscrollSpeedrun.TIME_TEXT);
            Widget personalBest = client.getWidget(InterfaceID.QuestscrollSpeedrun.BEST_TEXT);
            if (questName != null && duration != null && personalBest != null) {
                this.attemptNotify(QuestUtils.parseQuestWidget(questName.getText()), duration.getText(),
                    personalBest.getText(), profiles);
            } else {
                log.warn("Found speedrun finished widget (group id {}) but it is missing something, questName={}, duration={}, pb={}", event.getGroupId(), questName, duration, personalBest);
            }
        }
    }

    private void attemptNotify(String questName, String duration, String pb, ProfileRuntimeSnapshot profiles) {
        if (!profiles.isProfilesConfigured() && !isPersonalBest && config.speedrunPBOnly()) {
            return;
        }

        // Reformat the durations for the extra object
        Duration bestTime = TimeUtils.parseTime(pb);
        Duration currentTime = TimeUtils.parseTime(duration);

        SpeedrunNotificationData extra = new SpeedrunNotificationData(
            questName, bestTime.toString(), currentTime.toString(), isPersonalBest);
        if (profiles.isProfilesConfigured()) {
            NotificationBody<?> outbound = null;
            for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
                if (!isProfileEnabled(profile) || (!isPersonalBest && profile.speedrunPBOnly())) continue;
                NotificationBody<?> body = createBody(profile, questName, duration, pb, extra);
                if (deliver(profile, profile.speedrunWebhook(), profile.speedrunSendImage(), body)
                    && outbound == null) outbound = body;
            }
            if (outbound != null) publishPluginMessage(outbound);
            this.reset();
            return;
        }

        createMessage(config.speedrunSendImage(), createBody(config, questName, duration, pb, extra));
        this.reset();
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, String questName,
                                            String duration, String pb, SpeedrunNotificationData extra) {
        Template notifyMessage = Template.builder()
            .template(isPersonalBest ? deliveryConfig.speedrunPBMessage() : deliveryConfig.speedrunMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
            .replacement("%QUEST%", Replacements.ofWiki(questName))
            .replacement("%TIME%", Replacements.ofText(duration))
            .replacement("%BEST%", Replacements.ofText(pb))
            .build();
        return NotificationBody.builder()
            .text(notifyMessage)
            .extra(extra)
            .type(NotificationType.SPEEDRUN)
            .build();
    }

    public void onGameMessage(String chatMessage) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (!enabled) {
            return;
        }

        if (chatMessage.startsWith("Speedrun duration: ")) {
            isPersonalBest = chatMessage.endsWith(" (new personal best)");
        }
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifySpeedrun()
            && profileEligibilityService.isEligible(profile, false, false, true);
    }
}
