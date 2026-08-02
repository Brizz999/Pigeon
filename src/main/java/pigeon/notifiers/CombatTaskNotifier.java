package pigeon.notifiers;

import com.google.common.collect.ImmutableMap;
import pigeon.PigeonConfig;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.util.Utils;
import pigeon.domain.CombatAchievementTier;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.notifiers.data.CombatAchievementData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CombatTaskNotifier extends BaseNotifier {
    private static final Pattern ACHIEVEMENT_PATTERN = Pattern.compile("Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+)\\.");
    private static final Pattern TASK_POINTS = Pattern.compile("\\s+\\(\\d+ points?\\)$");
    public static final String REPEAT_WARNING = "Combat Task notifier will fire duplicates unless you disable the game setting: Combat Achievement Tasks - Repeat completion";

    /**
     * @see <a href="https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bproc,ca_tasks_progress_bar%5D.cs2#L6-L11">CS2 Reference</a>
     */
    @VisibleForTesting
    public static final Map<CombatAchievementTier, Integer> CUM_POINTS_VARBIT_BY_TIER;

    /**
     * The cumulative points needed to unlock rewards for each tier, in a Red-Black tree.
     * <p>
     * This is populated by {@link #initThresholds()} based on {@link #CUM_POINTS_VARBIT_BY_TIER}.
     *
     * @see <a href="https://gachi.gay/01CAv">Rewards Thresholds at the launch of the points-based system</a>
     */
    private final NavigableMap<Integer, CombatAchievementTier> cumulativeUnlockPoints = new TreeMap<>();

    @Inject
    private ClientThread clientThread;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyCombatTask() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.combatTaskWebhook();
    }

    public void onTick() {
        if (cumulativeUnlockPoints.size() < CUM_POINTS_VARBIT_BY_TIER.size())
            initThresholds();
    }

    public void onGameMessage(String message) {
        parse(message).ifPresent(pair -> {
            ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
            boolean enabled = profiles.isProfilesConfigured()
                ? profiles.getEnabledProfiles().stream().anyMatch(profile ->
                    isProfileEnabled(profile)
                        && pair.getLeft().ordinal() >= profile.minCombatAchievementTier().ordinal())
                : isEnabled();
            if (enabled) handle(pair.getLeft(), pair.getRight(), profiles);
        });
    }

    private void handle(CombatAchievementTier tier, String task, ProfileRuntimeSnapshot profiles) {
        if (!profiles.isProfilesConfigured()
            && tier.ordinal() < config.minCombatAchievementTier().ordinal())
            return;

        // delay notification for varbits to be updated
        clientThread.invokeAtTickEnd(() -> {
            int taskPoints = tier.getPoints();
            int totalPoints = client.getVarbitValue(VarbitID.CA_POINTS);
            int totalPossiblePoints = client.getVarbitValue(VarbitID.CA_THRESHOLD_GRANDMASTER);

            var nextThreshold = cumulativeUnlockPoints.ceilingEntry(totalPoints + 1);
            Map.Entry<Integer, CombatAchievementTier> prev = cumulativeUnlockPoints.floorEntry(totalPoints);
            int prevThreshold = prev != null ? prev.getKey() : 0;

            Integer tierProgress, tierTotalPoints;
            if (nextThreshold != null) {
                tierProgress = totalPoints - prevThreshold;
                tierTotalPoints = nextThreshold.getKey() - prevThreshold;
            } else {
                tierProgress = tierTotalPoints = null;
            }

            boolean crossedThreshold = prevThreshold > 0 && totalPoints - taskPoints < prevThreshold;
            CombatAchievementTier completedTier = crossedThreshold ? prev.getValue() : null;
            String completedTierName = completedTier != null ? completedTier.getDisplayName() : "N/A";
            CombatAchievementTier currentTier = crossedThreshold || prev == null ? null : prev.getValue();
            CombatAchievementTier nextTier = nextThreshold != null ? nextThreshold.getValue() : null;

            String player = Utils.getPlayerName(client);
            CombatAchievementData extra = new CombatAchievementData(
                tier, task, taskPoints, totalPoints, tierProgress, tierTotalPoints,
                totalPossiblePoints, currentTier, nextTier, completedTier);
            if (profiles.isProfilesConfigured()) {
                NotificationBody<?> outbound = null;
                for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
                    if (!isProfileEnabled(profile)
                        || tier.ordinal() < profile.minCombatAchievementTier().ordinal()) continue;
                    NotificationBody<?> body = createBody(
                        profile, tier, task, taskPoints, totalPoints, completedTierName,
                        crossedThreshold, player, extra);
                    if (deliver(profile, profile.combatTaskWebhook(), profile.combatTaskSendImage(), body)
                        && outbound == null) outbound = body;
                }
                if (outbound != null) publishPluginMessage(outbound);
                return;
            }

            createMessage(config.combatTaskSendImage(), createBody(
                config, tier, task, taskPoints, totalPoints, completedTierName,
                crossedThreshold, player, extra));
        });
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, CombatAchievementTier tier,
                                            String task, int taskPoints, int totalPoints,
                                            String completedTierName, boolean crossedThreshold,
                                            String player, CombatAchievementData extra) {
        Template message = Template.builder()
                .template(crossedThreshold
                    ? deliveryConfig.combatTaskUnlockMessage() : deliveryConfig.combatTaskMessage())
                .replacementBoundary("%")
                .replacement("%USERNAME%", Replacements.ofText(player))
                .replacement("%TIER%", Replacements.ofText(tier.toString()))
                .replacement("%TASK%", Replacements.ofWiki(task))
                .replacement("%POINTS%", Replacements.ofText(String.valueOf(taskPoints)))
                .replacement("%TOTAL_POINTS%", Replacements.ofText(String.valueOf(totalPoints)))
                .replacement("%COMPLETED%", Replacements.ofText(completedTierName))
                .build();

        return NotificationBody.<CombatAchievementData>builder()
            .type(NotificationType.COMBAT_ACHIEVEMENT)
            .text(message)
            .playerName(player)
            .extra(extra)
            .build();
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyCombatTask() && profileEligibilityService.isEligible(profile);
    }

    private void initThresholds() {
        CUM_POINTS_VARBIT_BY_TIER.forEach((tier, varbitId) -> {
            int cumulativePoints = client.getVarbitValue(varbitId);
            if (cumulativePoints > 0)
                cumulativeUnlockPoints.put(cumulativePoints, tier);
        });
    }

    @VisibleForTesting
    static Optional<Pair<CombatAchievementTier, String>> parse(String message) {
        Matcher matcher = ACHIEVEMENT_PATTERN.matcher(message);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(matcher.group("tier"))
            .map(CombatAchievementTier.TIER_BY_LOWER_NAME::get)
            .map(tier -> Pair.of(
                tier,
                TASK_POINTS.matcher(
                    matcher.group("task")
                ).replaceFirst("") // remove points suffix
            ));
    }

    static {
        // noinspection UnstableApiUsage (builderWithExpectedSize is no longer @Beta in snapshot guava)
        CUM_POINTS_VARBIT_BY_TIER = ImmutableMap.<CombatAchievementTier, Integer>builderWithExpectedSize(6)
            .put(CombatAchievementTier.EASY, VarbitID.CA_THRESHOLD_EASY) // 33 = 33 * 1
            .put(CombatAchievementTier.MEDIUM, VarbitID.CA_THRESHOLD_MEDIUM) // 115 = 33 + 41 * 2
            .put(CombatAchievementTier.HARD, VarbitID.CA_THRESHOLD_HARD) // 304 = 115 + 63 * 3
            .put(CombatAchievementTier.ELITE, VarbitID.CA_THRESHOLD_ELITE) // 820 = 304 + 129 * 4
            .put(CombatAchievementTier.MASTER, VarbitID.CA_THRESHOLD_MASTER) // 1465 = 820 + 129 * 5
            .put(CombatAchievementTier.GRANDMASTER, VarbitID.CA_THRESHOLD_GRANDMASTER) // 2005 = 1465 + 90 * 6
            .build();
    }
}
