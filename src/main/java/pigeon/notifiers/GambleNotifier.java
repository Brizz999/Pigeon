package pigeon.notifiers;

import com.google.common.collect.ImmutableSet;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Evaluable;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.message.templating.impl.JoiningReplacement;
import pigeon.notifiers.data.GambleNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ItemSearcher;
import pigeon.util.ItemUtils;
import pigeon.util.Utils;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class GambleNotifier extends BaseNotifier {
    // itemName (x quantity)![ tertiaryReward!] gc: n
    private static final Pattern GAMBLE_REGEX = Pattern.compile("^(.+?)!\\s*(.+!)?\\s+High level gamble count: (\\d+).*");
    private static final Pattern ITEM_QUANTITY_REGEX = Pattern.compile("^(.+?)\\(x (\\d+)\\)$");
    // penance pet is not actually present in dialog message, but it's here in case it's ever added
    private static final Collection<String> RARE_LOOT = ImmutableSet.of("dragon chainbody", "dragon med helm", "pet penance queen");

    @Inject
    private ItemSearcher itemSearcher;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyGamble() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.gambleWebhook();
    }

    public void onMesBoxNotification(String message) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (!enabled) return;
        ParsedData data = parse(message);
        if (data != null) {
            handleNotify(data, profiles);
        }
    }

    private void handleNotify(ParsedData data, ProfileRuntimeSnapshot profiles) {
        if (profiles.isProfilesConfigured()) {
            List<PigeonProfileConfig> recipients = profiles.getEnabledProfiles().stream()
                .filter(this::isProfileEnabled)
                .filter(profile -> messageFormat(profile, data) != null)
                .collect(Collectors.toList());
            if (recipients.isEmpty()) return;
            List<SerializedItemStack> items = serializeItems(data);
            GambleNotificationData extra = new GambleNotificationData(data.gambleCount, items);
            NotificationBody<?> outbound = null;
            for (PigeonProfileConfig profile : recipients) {
                NotificationBody<?> body = createBody(profile, data, items, extra);
                if (deliver(profile, profile.gambleWebhook(), profile.gambleSendImage(), body)
                    && outbound == null) outbound = body;
            }
            if (outbound != null) publishPluginMessage(outbound);
            return;
        }

        if (messageFormat(config, data) == null) return;
        List<SerializedItemStack> items = serializeItems(data);
        createMessage(config.gambleSendImage(), createBody(
            config, data, items, new GambleNotificationData(data.gambleCount, items)));
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, ParsedData data,
                                            List<SerializedItemStack> items,
                                            GambleNotificationData extra) {
        String player = Utils.getPlayerName(client);
        Template message = Template.builder()
            .template(messageFormat(deliveryConfig, data))
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(player))
            .replacement("%COUNT%", Replacements.ofText(String.valueOf(data.gambleCount)))
            .replacement("%LOOT%", lootSummary(items))
            .build();
        return NotificationBody.builder()
            .text(message)
            .extra(extra)
            .type(NotificationType.BARBARIAN_ASSAULT_GAMBLE)
            .build();
    }

    private String messageFormat(PigeonConfig deliveryConfig, ParsedData data) {
        if (deliveryConfig.gambleRareLoot() && RARE_LOOT.contains(data.itemName.toLowerCase())) {
            return deliveryConfig.gambleRareNotifyMessage();
        }
        return data.gambleCount % deliveryConfig.gambleInterval() == 0
            ? deliveryConfig.gambleNotifyMessage() : null;
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyGamble() && profileEligibilityService.isEligible(profile);
    }

    private static Evaluable lootSummary(List<SerializedItemStack> items) {
        JoiningReplacement.JoiningReplacementBuilder builder = JoiningReplacement.builder().delimiter("\n");
        items.forEach(item -> builder.component(ItemUtils.templateStack(item, true)));
        return builder.build();
    }

    private List<SerializedItemStack> serializeItems(ParsedData data) {
        return Stream.of(Pair.of(data.itemName, data.itemQuantity), Pair.of(data.tertiaryItem, 1))
            .filter(p -> p.getLeft() != null)
            .map(p -> Pair.of(itemSearcher.findItemId(p.getLeft()), p.getRight()))
            .filter(p -> p.getLeft() != null)
            .map(p -> ItemUtils.stackFromItem(itemManager, p.getLeft(), p.getRight()))
            .collect(Collectors.toList());
    }

    @Nullable
    @VisibleForTesting
    static ParsedData parse(String message) {
        Matcher matcher = GAMBLE_REGEX.matcher(message);
        if (!matcher.matches()) return null;
        String rawItem = matcher.group(1).trim();
        String itemName = rawItem;
        int itemQuantity = 1;
        Matcher quantityMatcher = ITEM_QUANTITY_REGEX.matcher(rawItem);
        if (quantityMatcher.matches()) {
            itemName = quantityMatcher.group(1).trim();
            itemQuantity = Integer.parseInt(quantityMatcher.group(2));
        }
        String tertiary = matcher.group(2);
        if (tertiary != null) {
            tertiary = StringUtils.removeEnd(tertiary, "!").trim();
        }
        int gambleCount = Integer.parseInt(matcher.group(3));
        return new ParsedData(itemName, itemQuantity, tertiary, gambleCount);
    }

    @Value
    @VisibleForTesting
    static class ParsedData {
        @NonNull
        String itemName;
        int itemQuantity;
        // https://oldschool.runescape.wiki/w/Barbarian_Assault/Rewards#Tertiary_High_Gamble_Rewards
        @Nullable
        String tertiaryItem;
        int gambleCount;
    }
}
