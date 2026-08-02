package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.domain.LootCriteria;
import pigeon.message.Embed;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Evaluable;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.message.templating.impl.JoiningReplacement;
import pigeon.notifiers.data.AnnotatedItemStack;
import pigeon.notifiers.data.LootNotificationData;
import pigeon.notifiers.data.RareItemStack;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ConfigUtil;
import pigeon.util.ItemUtils;
import pigeon.util.KillCountService;
import pigeon.util.MathUtils;
import pigeon.util.ThievingService;
import pigeon.util.RarityService;
import pigeon.util.Utils;
import pigeon.util.WorldUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.loottracker.LootRecordType;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class LootNotifier extends BaseNotifier {

    public static final Set<Integer> SERVER_LOOT_NPC_IDS;

    @Inject
    private ItemManager itemManager;

    @Inject
    private KillCountService killCountService;

    @Inject
    private RarityService rarityService;

    @Inject
    private ThievingService thievingService;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    private final Collection<Pattern> itemNameAllowlist = new CopyOnWriteArrayList<>();
    private final Collection<Pattern> itemNameDenylist = new CopyOnWriteArrayList<>();
    private final Collection<String> sourceDenylist = new CopyOnWriteArraySet<>();

    @Override
    public boolean isEnabled() {
        return config.notifyLoot() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.lootWebhook();
    }

    public void init() {
        itemNameAllowlist.clear();
        itemNameAllowlist.addAll(
            ConfigUtil.readDelimited(config.lootItemAllowlist())
                .map(Utils::regexify)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        itemNameDenylist.clear();
        itemNameDenylist.addAll(
            ConfigUtil.readDelimited(config.lootItemDenylist())
                .map(Utils::regexify)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        sourceDenylist.clear();
        sourceDenylist.addAll(
            ConfigUtil.readDelimited(config.lootSourceDenylist())
                .map(String::toLowerCase)
                .collect(Collectors.toList())
        );
    }

    public void onConfigChanged(String key, String value) {
        if ("lootSourceDenylist".equals(key)) {
            sourceDenylist.clear();
            sourceDenylist.addAll(
                ConfigUtil.readDelimited(value)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList())
            );
            return;
        }

        Collection<Pattern> itemNames;
        if ("lootItemAllowlist".equals(key)) {
            itemNames = itemNameAllowlist;
        } else if ("lootItemDenylist".equals(key)) {
            itemNames = itemNameDenylist;
        } else {
            return;
        }

        itemNames.clear();
        itemNames.addAll(
            ConfigUtil.readDelimited(value)
                .map(Utils::regexify)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
    }

    public void onServerNpcLoot(ServerNpcLoot event) {
        var comp = event.getComposition();
        this.handleNotify(event.getItems(), comp.getName(), LootRecordType.NPC, comp.getId(), false, false);
    }

    public void onNpcLootReceived(NpcLootReceived event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (KillCountService.SPECIAL_LOOT_NPC_IDS.contains(id)) {
            // LootReceived is fired for certain NPCs rather than NpcLootReceived, but return here just in case upstream changes their implementation.
            return;
        }

        this.handleNotify(event.getItems(), npc.getName(), LootRecordType.NPC, id, false, false);
    }

    public void onPlayerLootReceived(PlayerLootReceived event) {
        if (WorldUtils.isSafeArea(client))
            return;

        this.handleNotify(event.getItems(), event.getPlayer().getName(), LootRecordType.PLAYER, null, false, false);
    }

    public void onLootReceived(LootReceived lootReceived) {
        // only consider non-NPC and non-PK loot
        if (lootReceived.getType() == LootRecordType.EVENT || lootReceived.getType() == LootRecordType.PICKPOCKET) {
            String source = killCountService.getStandardizedSource(lootReceived);
            boolean gamble = "Barbarian Assault high gamble".equals(lootReceived.getName());
            boolean clue = StringUtils.startsWithIgnoreCase(lootReceived.getName(), "Clue Scroll");
            this.handleNotify(lootReceived.getItems(), source, lootReceived.getType(), null, gamble, clue);
        } else if (lootReceived.getType() == LootRecordType.NPC && KillCountService.SPECIAL_LOOT_NPC_NAMES.contains(lootReceived.getName())) {
            // Special case: upstream fires LootReceived for certain NPCs, but not NpcLootReceived
            String source = killCountService.getStandardizedSource(lootReceived);
            var type = KillCountService.THE_GAUNTLET.equals(source) || KillCountService.CG_NAME.equals(source) ? LootRecordType.EVENT : lootReceived.getType();
            this.handleNotify(lootReceived.getItems(), source, type, null, false, false);
        }
    }

    public void onGameMessage(String message) {
        if ("You have found a Pharaoh's sceptre! It fell on the floor.".equals(message)) {
            this.handleNotify(List.of(new ItemStack(ItemID.PHARAOHS_SCEPTRE, 1)), "Pyramid Plunder", LootRecordType.EVENT, null, false, false);
        }
    }

    private void handleNotify(Collection<ItemStack> items, String dropper, LootRecordType type, Integer npcId,
                              boolean gamble, boolean clue) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        if (profiles.isProfilesConfigured()) {
            handleProfileNotify(items, dropper, type, npcId, gamble, clue, profiles);
            return;
        }
        if (!isEnabled()
            || (type == LootRecordType.PLAYER && !config.includePlayerLoot())
            || (gamble && !config.lootIncludeGambles())
            || (clue && !config.lootIncludeClueScrolls())) {
            return;
        }
        handleLegacyNotify(items, dropper, type, npcId);
    }

    private void handleLegacyNotify(Collection<ItemStack> items, String dropper, LootRecordType type, Integer npcId) {
        if (type != LootRecordType.PLAYER && sourceDenylist.contains(dropper.toLowerCase())) {
            log.debug("Skipping loot notif for denied loot source: {} ({})", dropper, type);
            return;
        }

        final Integer kc = killCountService.getKillCount(type, dropper);
        final int minValue = config.minLootValue();
        final boolean icons = config.lootIcons();

        Collection<ItemStack> reduced = ItemUtils.reduceItemStack(items);
        List<SerializedItemStack> serializedItems = new ArrayList<>(reduced.size());
        List<Embed> embeds = new ArrayList<>(icons ? reduced.size() : 0);

        JoiningReplacement.JoiningReplacementBuilder lootMessage = JoiningReplacement.builder().delimiter("\n");
        long totalStackValue = 0;
        boolean sendMessage = false;
        boolean onAllowList = false;
        SerializedItemStack max = null;
        RareItemStack rarest = null;

        final double rarityThreshold = config.lootRarityThreshold() > 0 ? 1.0 / config.lootRarityThreshold() : Double.NaN;
        final boolean intersection = config.lootRarityValueIntersection() && Double.isFinite(rarityThreshold);
        for (ItemStack item : reduced) {
            SerializedItemStack stack = ItemUtils.stackFromItem(itemManager, item.getId(), item.getQuantity());
            long totalPrice = stack.getTotalPrice();

            OptionalDouble rarity;
            if (type == LootRecordType.NPC) {
                rarity = rarityService.getRarity(dropper, item.getId(), item.getQuantity());
            } else if (type == LootRecordType.PICKPOCKET) {
                rarity = thievingService.getRarity(dropper, item.getId(), item.getQuantity());
            } else {
                rarity = OptionalDouble.empty();
            }

            boolean shouldSend;
            var criteria = EnumSet.noneOf(LootCriteria.class);
            if (totalPrice >= minValue) {
                criteria.add(LootCriteria.VALUE);
            }
            if (MathUtils.lessThanOrEqual(rarity.orElse(1), rarityThreshold)) {
                criteria.add(LootCriteria.RARITY);
            }
            if (intersection) {
                shouldSend = criteria.contains(LootCriteria.VALUE) && (rarity.isEmpty() || criteria.contains(LootCriteria.RARITY));
            } else {
                shouldSend = criteria.contains(LootCriteria.VALUE) || criteria.contains(LootCriteria.RARITY);
            }

            boolean denied = matches(itemNameDenylist, stack.getName());
            if (denied) {
                shouldSend = false;
                criteria.add(LootCriteria.DENYLIST);
            } else {
                if (matches(itemNameAllowlist, stack.getName())) {
                    shouldSend = true;
                    onAllowList = true;
                    criteria.add(LootCriteria.ALLOWLIST);
                }
                if (max == null || totalPrice > max.getTotalPrice()) {
                    max = stack;
                }
            }

            if (shouldSend) {
                sendMessage = true;
                lootMessage.component(ItemUtils.templateStack(stack, true));
                if (icons) embeds.add(Embed.ofImage(ItemUtils.getItemImageUrl(item.getId())));
            }

            var annotated = AnnotatedItemStack.of(stack, criteria);
            if (rarity.isPresent()) {
                RareItemStack rareStack = RareItemStack.of(annotated, rarity.getAsDouble());
                serializedItems.add(rareStack);
                if (!denied && (rarest == null || rareStack.getRarity() < rarest.getRarity())) {
                    rarest = rareStack;
                }
            } else {
                serializedItems.add(annotated);
            }
            totalStackValue += totalPrice;
        }

        Evaluable lootMsg;
        if (!sendMessage) {
            if (totalStackValue >= minValue && max != null && "Loot Chest".equalsIgnoreCase(dropper)) {
                // Special case: PK loot keys should trigger notification if total value exceeds configured minimum even
                // if no single item itself would exceed the min value config - github.com/pajlads/DinkPlugin/issues/403
                sendMessage = true;
                lootMsg = Replacements.ofMultiple(" ",
                    Replacements.ofText("Various items including:"),
                    ItemUtils.templateStack(max, true)
                );
            } else {
                lootMsg = null;
            }
        } else {
            lootMsg = lootMessage.build();
        }

        if (sendMessage) {
            if (npcId == null && (type == LootRecordType.NPC || type == LootRecordType.PICKPOCKET)) {
                npcId = client.getTopLevelWorldView().npcs().stream()
                    .filter(npc -> dropper.equals(npc.getName()))
                    .findAny()
                    .map(NPC::getId)
                    .orElse(null);
            }

            String overrideUrl = getWebhookUrl();
            if (config.lootRedirectPlayerKill() && !config.pkWebhook().isBlank()) {
                if (type == LootRecordType.PLAYER || (type == LootRecordType.EVENT && "Loot Chest".equals(dropper))) {
                    overrideUrl = config.pkWebhook();
                }
            }
            Double rarity = rarest != null ? rarest.getRarity() : null;
            boolean screenshot = config.lootSendImage() && (totalStackValue >= config.lootImageMinValue() || onAllowList);
            Collection<String> party = type == LootRecordType.EVENT ? Utils.getBossParty(client, dropper) : null;
            Evaluable source = type == LootRecordType.PLAYER
                ? Replacements.ofLink(dropper, config.playerLookupService().getPlayerUrl(dropper))
                : Replacements.ofWiki(dropper);
            Template notifyMessage = Template.builder()
                .template(config.lootNotifyMessage())
                .replacementBoundary("%")
                .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
                .replacement("%LOOT%", lootMsg)
                .replacement("%TOTAL_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(totalStackValue)))
                .replacement("%SOURCE%", source)
                .replacement("%COUNT%", Replacements.ofText(kc != null ? kc.toString() : "unknown"))
                .build();
            createMessage(overrideUrl, screenshot,
                NotificationBody.builder()
                    .text(notifyMessage)
                    .embeds(embeds)
                    .extra(new LootNotificationData(serializedItems, dropper, type, kc, rarity, party, npcId))
                    .type(NotificationType.LOOT)
                    .thumbnailUrl(ItemUtils.getItemImageUrl(max.getId()))
                    .build()
            );
        }
    }

    private void handleProfileNotify(Collection<ItemStack> items, String dropper, LootRecordType type,
                                     Integer npcId, boolean gamble, boolean clue,
                                     ProfileRuntimeSnapshot profiles) {
        List<PigeonProfileConfig> candidates = profiles.getEnabledProfiles().stream()
            .filter(profile -> profile.notifyLoot() && profileEligibilityService.isEligible(profile))
            .filter(profile -> type != LootRecordType.PLAYER || profile.includePlayerLoot())
            .filter(profile -> !gamble || profile.lootIncludeGambles())
            .filter(profile -> !clue || profile.lootIncludeClueScrolls())
            .filter(profile -> !sourceDenied(profile, dropper, type))
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return;

        Collection<ItemStack> reduced = ItemUtils.reduceItemStack(items);
        List<AnalyzedStack> analyzed = new ArrayList<>(reduced.size());
        long totalStackValue = 0;
        for (ItemStack item : reduced) {
            SerializedItemStack stack = ItemUtils.stackFromItem(itemManager, item.getId(), item.getQuantity());
            OptionalDouble rarity;
            if (type == LootRecordType.NPC) {
                rarity = rarityService.getRarity(dropper, item.getId(), item.getQuantity());
            } else if (type == LootRecordType.PICKPOCKET) {
                rarity = thievingService.getRarity(dropper, item.getId(), item.getQuantity());
            } else {
                rarity = OptionalDouble.empty();
            }
            analyzed.add(new AnalyzedStack(item, stack, rarity));
            totalStackValue += stack.getTotalPrice();
        }

        Integer kc = killCountService.getKillCount(type, dropper);
        Integer resolvedNpcId = resolveNpcId(npcId, type, dropper);
        Collection<String> party = type == LootRecordType.EVENT ? Utils.getBossParty(client, dropper) : null;
        NotificationBody<?> outbound = null;
        for (PigeonProfileConfig profile : candidates) {
            NotificationBody<?> body = createProfileBody(
                profile, analyzed, dropper, type, kc, resolvedNpcId, party, totalStackValue);
            if (body == null) continue;

            String overrideUrl = profile.lootWebhook();
            if (profile.lootRedirectPlayerKill() && !profile.pkWebhook().isBlank()
                && (type == LootRecordType.PLAYER
                    || (type == LootRecordType.EVENT && "Loot Chest".equals(dropper)))) {
                overrideUrl = profile.pkWebhook();
            }
            long total = totalStackValue;
            boolean allowlisted = ((LootNotificationData) body.getExtra()).getItems().stream()
                .filter(AnnotatedItemStack.class::isInstance)
                .map(AnnotatedItemStack.class::cast)
                .anyMatch(stack -> stack.getCriteria().contains(LootCriteria.ALLOWLIST));
            boolean screenshot = profile.lootSendImage()
                && (total >= profile.lootImageMinValue() || allowlisted);
            if (deliver(profile, overrideUrl, screenshot, body) && outbound == null) {
                outbound = body;
            }
        }
        if (outbound != null) publishPluginMessage(outbound);
    }

    private NotificationBody<?> createProfileBody(PigeonProfileConfig profile, List<AnalyzedStack> analyzed,
                                                   String dropper, LootRecordType type, Integer kc,
                                                   Integer npcId, Collection<String> party,
                                                   long totalStackValue) {
        Collection<Pattern> allowlist = patterns(profile.lootItemAllowlist());
        Collection<Pattern> denylist = patterns(profile.lootItemDenylist());
        int minValue = profile.minLootValue();
        double rarityThreshold = profile.lootRarityThreshold() > 0
            ? 1.0 / profile.lootRarityThreshold() : Double.NaN;
        boolean intersection = profile.lootRarityValueIntersection() && Double.isFinite(rarityThreshold);

        List<SerializedItemStack> serializedItems = new ArrayList<>(analyzed.size());
        List<Embed> embeds = new ArrayList<>(profile.lootIcons() ? analyzed.size() : 0);
        JoiningReplacement.JoiningReplacementBuilder lootMessage = JoiningReplacement.builder().delimiter("\n");
        boolean sendMessage = false;
        SerializedItemStack max = null;
        RareItemStack rarest = null;

        for (AnalyzedStack analyzedStack : analyzed) {
            SerializedItemStack stack = analyzedStack.stack;
            OptionalDouble rarity = analyzedStack.rarity;
            var criteria = EnumSet.noneOf(LootCriteria.class);
            if (stack.getTotalPrice() >= minValue) criteria.add(LootCriteria.VALUE);
            if (MathUtils.lessThanOrEqual(rarity.orElse(1), rarityThreshold)) criteria.add(LootCriteria.RARITY);

            boolean shouldSend = intersection
                ? criteria.contains(LootCriteria.VALUE)
                    && (rarity.isEmpty() || criteria.contains(LootCriteria.RARITY))
                : criteria.contains(LootCriteria.VALUE) || criteria.contains(LootCriteria.RARITY);
            boolean denied = matches(denylist, stack.getName());
            if (denied) {
                shouldSend = false;
                criteria.add(LootCriteria.DENYLIST);
            } else {
                if (matches(allowlist, stack.getName())) {
                    shouldSend = true;
                    criteria.add(LootCriteria.ALLOWLIST);
                }
                if (max == null || stack.getTotalPrice() > max.getTotalPrice()) max = stack;
            }

            if (shouldSend) {
                sendMessage = true;
                lootMessage.component(ItemUtils.templateStack(stack, true));
                if (profile.lootIcons()) {
                    embeds.add(Embed.ofImage(ItemUtils.getItemImageUrl(analyzedStack.item.getId())));
                }
            }

            AnnotatedItemStack annotated = AnnotatedItemStack.of(stack, criteria);
            if (rarity.isPresent()) {
                RareItemStack rareStack = RareItemStack.of(annotated, rarity.getAsDouble());
                serializedItems.add(rareStack);
                if (!denied && (rarest == null || rareStack.getRarity() < rarest.getRarity())) rarest = rareStack;
            } else {
                serializedItems.add(annotated);
            }
        }

        Evaluable lootMsg;
        if (!sendMessage && totalStackValue >= minValue && max != null
            && "Loot Chest".equalsIgnoreCase(dropper)) {
            sendMessage = true;
            lootMsg = Replacements.ofMultiple(" ", Replacements.ofText("Various items including:"),
                ItemUtils.templateStack(max, true));
        } else if (sendMessage) {
            lootMsg = lootMessage.build();
        } else {
            return null;
        }

        Evaluable source = type == LootRecordType.PLAYER
            ? Replacements.ofLink(dropper, profile.playerLookupService().getPlayerUrl(dropper))
            : Replacements.ofWiki(dropper);
        Template notifyMessage = Template.builder()
            .template(profile.lootNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(Utils.getPlayerName(client)))
            .replacement("%LOOT%", lootMsg)
            .replacement("%TOTAL_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(totalStackValue)))
            .replacement("%SOURCE%", source)
            .replacement("%COUNT%", Replacements.ofText(kc != null ? kc.toString() : "unknown"))
            .build();
        return NotificationBody.builder()
            .text(notifyMessage)
            .embeds(embeds)
            .extra(new LootNotificationData(serializedItems, dropper, type, kc,
                rarest != null ? rarest.getRarity() : null, party, npcId))
            .type(NotificationType.LOOT)
            .thumbnailUrl(ItemUtils.getItemImageUrl(max.getId()))
            .build();
    }

    private Integer resolveNpcId(Integer npcId, LootRecordType type, String dropper) {
        if (npcId != null || (type != LootRecordType.NPC && type != LootRecordType.PICKPOCKET)) return npcId;
        return client.getTopLevelWorldView().npcs().stream()
            .filter(npc -> dropper.equals(npc.getName()))
            .findAny()
            .map(NPC::getId)
            .orElse(null);
    }

    private boolean sourceDenied(PigeonConfig deliveryConfig, String dropper, LootRecordType type) {
        if (type == LootRecordType.PLAYER) return false;
        String source = dropper.toLowerCase(Locale.ROOT);
        return ConfigUtil.readDelimited(deliveryConfig.lootSourceDenylist())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(source::equals);
    }

    private Collection<Pattern> patterns(String value) {
        return ConfigUtil.readDelimited(value)
            .map(Utils::regexify)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static final class AnalyzedStack {
        private final ItemStack item;
        private final SerializedItemStack stack;
        private final OptionalDouble rarity;

        private AnalyzedStack(ItemStack item, SerializedItemStack stack, OptionalDouble rarity) {
            this.item = item;
            this.stack = stack;
            this.rarity = rarity;
        }
    }

    private static boolean matches(Collection<Pattern> regexps, String input) {
        for (Pattern regex : regexps) {
            if (regex.matcher(input).find())
                return true;
        }
        return false;
    }

    static {
        SERVER_LOOT_NPC_IDS = Set.of(
            NpcID.YAMA,
            NpcID.HESPORI,
            NpcID.SAILING_BULL_SHARK_DEAD,
            NpcID.SAILING_HAMMERHEAD_SHARK_DEAD,
            NpcID.SAILING_TIGER_SHARK_DEAD,
            NpcID.SAILING_GREAT_WHITE_SHARK_DEAD,
            NpcID.SAILING_NARWHAL_DEAD,
            NpcID.SAILING_ORCA_DEAD,
            NpcID.SAILING_PYGMY_KRAKEN_DEAD,
            NpcID.SAILING_SPINED_KRAKEN_DEAD,
            NpcID.SAILING_ARMOURED_KRAKEN_DEAD,
            NpcID.SAILING_VAMPYRE_KRAKEN_DEAD,
            NpcID.SAILING_EAGLE_RAY_DEAD,
            NpcID.SAILING_BUTTERFLY_RAY_DEAD,
            NpcID.SAILING_STINGRAY_DEAD,
            NpcID.SAILING_MANTA_RAY_DEAD,
            NpcID.SAILING_OSPREY_DEAD,
            NpcID.SAILING_ALBATROSS_DEAD,
            NpcID.SAILING_FRIGATEBIRD_DEAD,
            NpcID.SAILING_TERN_DEAD,
            NpcID.SAILING_SEA_MOGRE_DEAD,
            NpcID.SAILING_DOLPHIN_DEAD,
            NpcID.SAILING_VEILED_KRAKEN_DEAD,
            NpcID.MAGGOT_KING,
            NpcID.MAGGOT_KING_CORPSE,
            16305, 16306, 16307, 16308, 16309, 16310, 16311, 16312, 16313, 16314, 16315 // Mad Angel
        );
    }
}
