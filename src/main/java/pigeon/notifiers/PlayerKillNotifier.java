package pigeon.notifiers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.PlayerKillNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ItemUtils;
import pigeon.util.WorldUtils;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class PlayerKillNotifier extends BaseNotifier {

    private static final KitType[] EQUIPMENT;

    /**
     * Contains the damage inflicted by the local player on various targets in a short time period.
     * <p>
     * Uses weak keys to not hinder garbage collection
     * (and avoid synchronization if we were forced to do Map#clear on plugin shutdown).
     */
    private final Map<Player, Integer> attacked = new WeakHashMap<>(4);
    private final Map<Player, ProfileRuntimeSnapshot> attackProfiles = new WeakHashMap<>(4);

    private final Cache<Actor, Boolean> recentlyNotified = CacheBuilder.newBuilder()
        .weakKeys()
        .expireAfterAccess(5, TimeUnit.SECONDS)
        .build();

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        if (!config.notifyPk())
            return false;

        if (!worldTracker.hasValidState()) {
            // duplicated logic from super class but allow Duel Arena
            EnumSet<WorldType> world = client.getWorldType().clone(); // fast on RegularEnumSet
            world.remove(WorldType.PVP_ARENA);

            if (WorldUtils.isIgnoredWorld(world)) {
                return false;
            }
        }

        return accountTracker.hasValidState();
    }

    @Override
    protected String getWebhookUrl() {
        return config.pkWebhook();
    }

    public void onHitsplat(HitsplatApplied event) {
        Hitsplat hit = event.getHitsplat();
        int amount = hit.getAmount();
        if (amount <= 0 || !hit.isMine())
            return;

        Actor actor = event.getActor();
        if (actor == client.getLocalPlayer() || !(actor instanceof Player))
            return;

        // multi-tick spec already killed them on a previous tick - https://github.com/pajlads/DinkPlugin/issues/466
        if (recentlyNotified.getIfPresent(actor) != null)
            return;

        Player target = (Player) actor;
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : config.notifyPk();
        if (!enabled) return;
        attacked.merge(target, amount, Integer::sum);
        attackProfiles.putIfAbsent(target, profiles);
    }

    public void onTick() {
        // micro-optimization: this check is very fast for empty WeakHashMap & can avoid creating a HashIterator
        if (attacked.isEmpty())
            return;

        attacked.forEach((target, damage) -> {
            if (target.isDead())
                handleKill(target, damage, attackProfiles.get(target));
        });

        attacked.clear();
        attackProfiles.clear();
    }

    private void handleKill(Player target, int myLastDamage, ProfileRuntimeSnapshot profiles) {
        if (profiles != null && profiles.isProfilesConfigured()) {
            handleProfileKill(target, myLastDamage, profiles);
            return;
        }
        if (!isEnabled())
            return;

        if (config.pkSkipFriendly() && isFriendly(target))
            return;

        if (config.pkSkipSafe() && (WorldUtils.isSafeArea(client) || client.getWorldType().contains(WorldType.PVP_ARENA)))
            return;

        Map<KitType, SerializedItemStack> equipment = getEquipment(target.getPlayerComposition());
        long value = ItemUtils.getTotalPrice(equipment.values());
        long minValue = config.pkMinValue();
        if (value < minValue)
            return;

        boolean sendLocation = config.pkIncludeLocation();
        PlayerKillNotificationData extra = new PlayerKillNotificationData(
            target.getName(),
            target.getCombatLevel(),
            equipment,
            sendLocation ? client.getWorld() : null,
            sendLocation ? WorldUtils.getLocation(client, target) : null,
            client.getBoostedSkillLevel(Skill.HITPOINTS),
            myLastDamage
        );

        String localPlayer = client.getLocalPlayer().getName();
        Template message = Template.builder()
            .template(config.pkNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(localPlayer))
            .replacement("%TARGET%", Replacements.ofLink(target.getName(), config.playerLookupService().getPlayerUrl(target.getName())))
            .build();

        createMessage(config.pkSendImage(), NotificationBody.builder()
            .type(NotificationType.PLAYER_KILL)
            .text(message)
            .extra(extra)
            .playerName(localPlayer)
            .build());

        recentlyNotified.put(target, Boolean.TRUE);
    }

    private void handleProfileKill(Player target, int myLastDamage, ProfileRuntimeSnapshot profiles) {
        Map<KitType, SerializedItemStack> equipment = getEquipment(target.getPlayerComposition());
        long value = ItemUtils.getTotalPrice(equipment.values());
        boolean friendly = isFriendly(target);
        boolean safe = WorldUtils.isSafeArea(client)
            || client.getWorldType().contains(WorldType.PVP_ARENA);
        String localPlayer = client.getLocalPlayer().getName();
        NotificationBody<?> outbound = null;
        boolean accepted = false;

        for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
            if (!isProfileEnabled(profile)
                || (profile.pkSkipFriendly() && friendly)
                || (profile.pkSkipSafe() && safe)
                || value < profile.pkMinValue()) continue;
            accepted = true;

            boolean sendLocation = profile.pkIncludeLocation();
            PlayerKillNotificationData extra = new PlayerKillNotificationData(
                target.getName(), target.getCombatLevel(), equipment,
                sendLocation ? client.getWorld() : null,
                sendLocation ? WorldUtils.getLocation(client, target) : null,
                client.getBoostedSkillLevel(Skill.HITPOINTS), myLastDamage);
            Template message = Template.builder()
                .template(profile.pkNotifyMessage())
                .replacementBoundary("%")
                .replacement("%USERNAME%", Replacements.ofText(localPlayer))
                .replacement("%TARGET%", Replacements.ofLink(
                    target.getName(), profile.playerLookupService().getPlayerUrl(target.getName())))
                .build();
            NotificationBody<?> body = NotificationBody.builder()
                .type(NotificationType.PLAYER_KILL)
                .text(message)
                .extra(extra)
                .playerName(localPlayer)
                .build();
            if (deliver(profile, profile.pkWebhook(), profile.pkSendImage(), body)
                && outbound == null) outbound = body;
        }
        if (outbound != null) publishPluginMessage(outbound);
        if (accepted) recentlyNotified.put(target, Boolean.TRUE);
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyPk() && profileEligibilityService.isEligible(profile, true);
    }

    private boolean isFriendly(Player target) {
        return target.isFriend() || target.isClanMember() || target.isFriendsChatMember()
            || (target.getTeam() != 0 && target.getTeam() == client.getLocalPlayer().getTeam());
    }

    private Map<KitType, SerializedItemStack> getEquipment(PlayerComposition comp) {
        if (comp == null) return Collections.emptyMap();

        int[] equipmentIds = comp.getEquipmentIds();
        int n = equipmentIds.length;

        Map<KitType, SerializedItemStack> map = new EnumMap<>(KitType.class);
        for (KitType slot : EQUIPMENT) {
            int index = slot.getIndex();
            if (index >= n) continue;
            int id = equipmentIds[index];
            if (id >= PlayerComposition.ITEM_OFFSET) {
                SerializedItemStack item = ItemUtils.stackFromItem(itemManager, id - PlayerComposition.ITEM_OFFSET, 1);
                map.put(slot, item);
            }
        }
        return map;
    }

    static {
        // omits ARMS, HAIR, JAW because they don't correspond to items
        EQUIPMENT = new KitType[] {
            KitType.HEAD,
            KitType.CAPE,
            KitType.AMULET,
            KitType.WEAPON,
            KitType.TORSO,
            KitType.SHIELD,
            KitType.LEGS,
            KitType.HANDS,
            KitType.BOOTS
        };
    }
}
