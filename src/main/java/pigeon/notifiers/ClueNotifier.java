package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.domain.ClueTier;
import pigeon.message.Embed;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Evaluable;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.message.templating.impl.JoiningReplacement;
import pigeon.util.ItemUtils;
import pigeon.util.Utils;
import pigeon.notifiers.data.ClueNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class ClueNotifier extends BaseNotifier {
    private static final Pattern CLUE_SCROLL_REGEX = Pattern.compile("You have completed (?<scrollCount>\\d+) (?<scrollType>\\w+) Treasure Trails?\\.");
    private final AtomicInteger badTicks = new AtomicInteger(); // used to prevent notifs from using stale data
    private volatile int clueCount = -1;
    private volatile String clueType = "";
    private ProfileRuntimeSnapshot pendingProfileSnapshot;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyClue() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.clueWebhook();
    }

    public void onChatMessage(String chatMessage) {
        Map.Entry<String, Integer> result = parse(chatMessage);
        if (result != null) {
            ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
            boolean enabled = profiles.isProfilesConfigured()
                ? profiles.getEnabledProfiles().stream().anyMatch(profile ->
                    isProfileEnabled(profile) && checkClueTier(profile, result.getKey()))
                : isEnabled() && checkClueTier(result.getKey());
            if (enabled) {
                // game message always occurs before widget load; save this data
                this.clueCount = result.getValue();
                this.clueType = result.getKey();
                if (profiles.isProfilesConfigured()) pendingProfileSnapshot = profiles;
            }
        }
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        boolean enabled = pendingProfileSnapshot != null
            ? pendingProfileSnapshot.getEnabledProfiles().stream().anyMatch(profile ->
                isProfileEnabled(profile) && checkClueTier(profile, clueType))
            : isEnabled();
        if (event.getGroupId() == InterfaceID.TRAIL_REWARDSCREEN && enabled) {
            Widget clue = client.getWidget(InterfaceID.TrailRewardscreen.ITEMS);
            if (clue != null && !clueType.isEmpty()) {
                Widget[] children = clue.getChildren();
                if (children == null) return;

                Map<Integer, Integer> clueItems = new HashMap<>();
                for (Widget child : children) {
                    if (child == null) continue;

                    int quantity = child.getItemQuantity();
                    int itemId = child.getItemId();
                    if (itemId > -1 && quantity > 0) {
                        clueItems.merge(itemId, quantity, Integer::sum);
                    }
                }

                if (pendingProfileSnapshot != null) handleProfileNotify(clueItems, pendingProfileSnapshot);
                else handleNotify(clueItems);
            }
        }
    }

    public void onTick() {
        // Track how many ticks occur where we only have partial clue data
        if (!clueType.isEmpty())
            badTicks.getAndIncrement();

        // Clear data if 2 ticks pass with only partial parsing (both events should occur within same tick)
        if (badTicks.get() > 1)
            reset();
    }

    private void handleNotify(Map<Integer, Integer> clueItems) {
        JoiningReplacement.JoiningReplacementBuilder lootMessage = JoiningReplacement.builder().delimiter("\n");
        AtomicLong totalPrice = new AtomicLong();
        List<SerializedItemStack> itemStacks = new ArrayList<>(clueItems.size());
        List<Embed> embeds = new ArrayList<>(config.clueShowItems() ? clueItems.size() : 0);

        clueItems.forEach((itemId, quantity) -> {
            SerializedItemStack stack = ItemUtils.stackFromItem(itemManager, itemId, quantity);
            totalPrice.addAndGet(stack.getTotalPrice());
            itemStacks.add(stack);
            lootMessage.component(getItemMessage(stack, embeds));
        });

        if (totalPrice.get() >= config.clueMinValue()) {
            boolean screenshot = config.clueSendImage() && totalPrice.get() >= config.clueImageMinValue();
            Template notifyMessage = Template.builder()
                .template(config.clueNotifyMessage())
                .replacementBoundary("%")
                .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
                .replacement("%CLUE%", Replacements.ofWiki(clueType, "Clue scroll (" + clueType + ")"))
                .replacement("%COUNT%", Replacements.ofText(String.valueOf(clueCount)))
                .replacement("%TOTAL_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(totalPrice.get())))
                .replacement("%LOOT%", lootMessage.build())
                .build();
            String icon = String.format("https://oldschool.runescape.wiki/images/Clue_scroll_(%s).png", clueType.toLowerCase());
            createMessage(screenshot,
                NotificationBody.builder()
                    .text(notifyMessage)
                    .extra(new ClueNotificationData(clueType, clueCount, itemStacks))
                    .type(NotificationType.CLUE)
                    .embeds(embeds)
                    .thumbnailUrl(icon)
                    .build()
            );
        }

        this.reset();
    }

    private void handleProfileNotify(Map<Integer, Integer> clueItems, ProfileRuntimeSnapshot profiles) {
        JoiningReplacement.JoiningReplacementBuilder lootMessage = JoiningReplacement.builder().delimiter("\n");
        long totalPrice = 0;
        List<SerializedItemStack> itemStacks = new ArrayList<>(clueItems.size());
        for (Map.Entry<Integer, Integer> item : clueItems.entrySet()) {
            SerializedItemStack stack = ItemUtils.stackFromItem(
                itemManager, item.getKey(), item.getValue());
            totalPrice += stack.getTotalPrice();
            itemStacks.add(stack);
            lootMessage.component(ItemUtils.templateStack(stack, true));
        }

        ClueNotificationData extra = new ClueNotificationData(clueType, clueCount, itemStacks);
        String icon = String.format(
            "https://oldschool.runescape.wiki/images/Clue_scroll_(%s).png", clueType.toLowerCase());
        NotificationBody<?> outbound = null;
        for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
            if (!isProfileEnabled(profile) || !checkClueTier(profile, clueType)
                || totalPrice < profile.clueMinValue()) continue;

            List<Embed> embeds = profile.clueShowItems()
                ? itemStacks.stream().map(item -> Embed.ofImage(ItemUtils.getItemImageUrl(item.getId())))
                    .collect(java.util.stream.Collectors.toList())
                : List.of();
            Template notifyMessage = Template.builder()
                .template(profile.clueNotifyMessage())
                .replacementBoundary("%")
                .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
                .replacement("%CLUE%", Replacements.ofWiki(clueType, "Clue scroll (" + clueType + ")"))
                .replacement("%COUNT%", Replacements.ofText(String.valueOf(clueCount)))
                .replacement("%TOTAL_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(totalPrice)))
                .replacement("%LOOT%", lootMessage.build())
                .build();
            NotificationBody<?> body = NotificationBody.builder()
                .text(notifyMessage)
                .extra(extra)
                .type(NotificationType.CLUE)
                .embeds(embeds)
                .thumbnailUrl(icon)
                .build();
            boolean screenshot = profile.clueSendImage() && totalPrice >= profile.clueImageMinValue();
            if (deliver(profile, profile.clueWebhook(), screenshot, body) && outbound == null) outbound = body;
        }
        if (outbound != null) publishPluginMessage(outbound);
        reset();
    }

    private Evaluable getItemMessage(SerializedItemStack item, Collection<Embed> embeds) {
        if (config.clueShowItems())
            embeds.add(Embed.ofImage(ItemUtils.getItemImageUrl(item.getId())));
        return ItemUtils.templateStack(item, true);
    }

    private boolean checkClueTier(String clueType) {
        return checkClueTier(config, clueType);
    }

    private boolean checkClueTier(PigeonConfig deliveryConfig, String clueType) {
        ClueTier tier = ClueTier.parse(clueType);
        if (tier == null) {
            log.warn("Failed to parse clue tier: {}", clueType);
            return true; // permissive approach
        }
        return tier.ordinal() >= deliveryConfig.clueMinTier().ordinal();
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyClue() && profileEligibilityService.isEligible(profile);
    }

    public void reset() {
        this.clueCount = -1;
        this.clueType = "";
        this.badTicks.set(0);
        this.pendingProfileSnapshot = null;
    }

    @Nullable
    public static Map.Entry<String, Integer> parse(String gameMessage) {
        Matcher clueMatcher = CLUE_SCROLL_REGEX.matcher(gameMessage);
        if (!clueMatcher.find()) return null;
        String tier = clueMatcher.group("scrollType");
        String count = clueMatcher.group("scrollCount");
        return Map.entry(tier, Integer.parseInt(count));
    }

}
