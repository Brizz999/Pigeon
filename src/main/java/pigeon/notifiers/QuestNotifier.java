package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.util.QuestUtils;
import pigeon.util.Utils;
import pigeon.notifiers.data.QuestNotificationData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;

public class QuestNotifier extends BaseNotifier {

    @Inject
    private ClientThread clientThread;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyQuest() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.questWebhook();
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.QUESTSCROLL) {
            ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
            boolean enabled = profiles.isProfilesConfigured()
                ? profiles.getEnabledProfiles().stream()
                    .anyMatch(profile -> profile.notifyQuest() && profileEligibilityService.isEligible(profile))
                : isEnabled();
            if (!enabled) {
                return;
            }

            Widget quest = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
            if (quest != null) {
                String questText = quest.getText();
                // 1 tick delay to ensure relevant varbits have been processed by the client
                clientThread.invokeLater(() -> handleNotify(questText, profiles));
            }
        }
    }

    private void handleNotify(String questText, ProfileRuntimeSnapshot profiles) {
        int completedQuests = client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT);
        int totalQuests = client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT);
        boolean validQuests = completedQuests > 0 && totalQuests > 0;

        int questPoints = client.getVarpValue(VarPlayerID.QP);
        int totalQuestPoints = client.getVarbitValue(VarbitID.QP_MAX);
        boolean validPoints = questPoints > 0 && totalQuestPoints > 0;

        String parsed = QuestUtils.parseQuestWidget(questText);
        if (parsed == null) return;

        QuestNotificationData extra = new QuestNotificationData(
            parsed,
            validQuests ? completedQuests : null,
            validQuests ? totalQuests : null,
            validPoints ? questPoints : null,
            validPoints ? totalQuestPoints : null
        );

        if (!profiles.isProfilesConfigured()) {
            NotificationBody<?> body = createBody(config, parsed, extra);
            createMessage(config.questSendImage(), body);
            return;
        }

        NotificationBody<?> outboundBody = null;
        for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
            if (!profile.notifyQuest() || !profileEligibilityService.isEligible(profile)) {
                continue;
            }

            NotificationBody<?> body = createBody(profile, parsed, extra);
            deliver(profile, profile.questWebhook(), profile.questSendImage(), body);
            if (outboundBody == null) {
                outboundBody = body;
            }
        }
        if (outboundBody != null) {
            publishPluginMessage(outboundBody);
        }
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, String parsed,
                                            QuestNotificationData extra) {
        Template notifyMessage = Template.builder()
            .template(deliveryConfig.questNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
            .replacement("%QUEST%", Replacements.ofWiki(parsed))
            .build();

        return NotificationBody.builder()
            .text(notifyMessage)
            .extra(extra)
            .type(NotificationType.QUEST)
            .build();
    }
}
