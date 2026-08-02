package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.PlayerLookupService;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.PlayerKillNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import pigeon.util.HitsplatImpl;
import pigeon.util.WorldUtils;
import net.runelite.api.Actor;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.runelite.api.HitsplatID.DAMAGE_OTHER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlayerKillNotifierTest extends MockedNotifierTest {

    private static final String TARGET = "Romy";
    private static final int LEVEL = 99;
    private static final int MY_HP = 10;
    private static final int WORLD = 420;
    private static final WorldPoint LOCATION = new WorldPoint(3000, 4000, 0);
    private static final int WEAPON_PRICE = 100_000;
    private static final int TOP_PRICE = 120_000;
    private static final int LEGS_PRICE = 80_000;
    private static final int HAND_PRICE = 100_000;
    private static final int SHIELD_PRICE = 200;
    private static final int EQUIPMENT_VALUE = WEAPON_PRICE + TOP_PRICE + LEGS_PRICE + HAND_PRICE + SHIELD_PRICE;
    private static final Map<KitType, SerializedItemStack> EQUIPMENT;

    @Bind
    @InjectMocks
    PlayerKillNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyPk()).thenReturn(true);
        when(config.pkMinValue()).thenReturn(EQUIPMENT_VALUE);
        when(config.pkIncludeLocation()).thenReturn(true);
        when(config.pkNotifyMessage()).thenReturn("%USERNAME% has PK'd %TARGET%");
        when(config.playerLookupService()).thenReturn(PlayerLookupService.NONE);

        // init client mocks
        when(client.getWorld()).thenReturn(WORLD);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(MY_HP);

        // init item mocks
        for (SerializedItemStack item : EQUIPMENT.values()) {
            mockItem(item.getId(), item.getPriceEach(), item.getName());
        }
    }

    @Test
    void testNotify() {
        // init mocks
        when(config.playerLookupService()).thenReturn(PlayerLookupService.OSRS_HISCORE);
        Player target = mockPlayer();

        // fire event
        int damage = 12;
        notifier.onHitsplat(event(target, damage));
        notifier.onTick();

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template(PLAYER_NAME + " has PK'd {{target}}")
                        .replacement("{{target}}", Replacements.ofLink(TARGET, PlayerLookupService.OSRS_HISCORE.getPlayerUrl(TARGET)))
                        .build()
                )
                .type(NotificationType.PLAYER_KILL)
                .playerName(PLAYER_NAME)
                .extra(new PlayerKillNotificationData(TARGET, LEVEL, EQUIPMENT, WORLD, LOCATION, MY_HP, damage))
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneTrackedKill() {
        PigeonProfile clan = pkProfile(
            "Clan", true, EQUIPMENT_VALUE, true,
            "Clan: %USERNAME% defeated %TARGET%", "https://example.com/clan");
        PigeonProfile friends = pkProfile(
            "Friends", true, EQUIPMENT_VALUE - 1, false,
            "Friends: %USERNAME% defeated %TARGET%", "https://example.com/friends");
        PigeonProfile expensive = pkProfile(
            "Expensive", true, EQUIPMENT_VALUE + 1, false,
            "Expensive", "https://example.com/expensive");
        PigeonProfile disabled = pkProfile(
            "Disabled", false, 0, false,
            "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, expensive, disabled);

        Player target = mockPlayer();
        notifier.onHitsplat(event(target, 12));
        notifier.onTick();

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: " + PLAYER_NAME + " defeated " + TARGET,
            bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: " + PLAYER_NAME + " defeated " + TARGET,
            bodies.getAllValues().get(1).getText().evaluate(false));
        PlayerKillNotificationData clanData = (PlayerKillNotificationData) bodies.getAllValues().get(0).getExtra();
        PlayerKillNotificationData friendsData = (PlayerKillNotificationData) bodies.getAllValues().get(1).getExtra();
        org.junit.jupiter.api.Assertions.assertEquals(WORLD, clanData.getWorld());
        org.junit.jupiter.api.Assertions.assertNull(friendsData.getWorld());
    }

    @Test
    void testNotifyMulti() {
        // init mocks
        Player target = mockPlayer();

        // fire events
        notifier.onHitsplat(event(target, 5));
        notifier.onHitsplat(event(target, 7));
        notifier.onTick();

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(String.format("%s has PK'd %s", PLAYER_NAME, TARGET)))
                .type(NotificationType.PLAYER_KILL)
                .playerName(PLAYER_NAME)
                .extra(new PlayerKillNotificationData(TARGET, LEVEL, EQUIPMENT, WORLD, LOCATION, MY_HP, 12))
                .build()
        );
    }

    @Test
    void testNotifyMultiOther() {
        // init mocks
        Player target = mockPlayer();

        // fire event
        int damage = 12;
        notifier.onHitsplat(event(target, damage));
        HitsplatApplied eventOther = new HitsplatApplied();
        eventOther.setActor(target);
        eventOther.setHitsplat(new HitsplatImpl(DAMAGE_OTHER, 13, 1));
        notifier.onHitsplat(eventOther);
        notifier.onTick();

        // verify notification
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(buildTemplate(String.format("%s has PK'd %s", PLAYER_NAME, TARGET)))
                .type(NotificationType.PLAYER_KILL)
                .playerName(PLAYER_NAME)
                .extra(new PlayerKillNotificationData(TARGET, LEVEL, EQUIPMENT, WORLD, LOCATION, MY_HP, damage))
                .build()
        );
    }

    @Test
    void testIgnoreValue() {
        // init mocks
        Player target = mockPlayer();
        target.getPlayerComposition().getEquipmentIds()[KitType.SHIELD.getIndex()] = 0;

        // fire event
        notifier.onHitsplat(event(target, 13));
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreZero() {
        // init mocks
        Player target = mockPlayer();

        // fire event
        notifier.onHitsplat(event(target, 0));
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreSafe() {
        // init mocks
        Player target = mockPlayer();
        when(config.pkSkipSafe()).thenReturn(true);
        when(localPlayer.getWorldLocation()).thenReturn(
            WorldPoint.fromRegion(WorldUtils.TZHAAR_PIT, 0, 0, 0)
        );

        // fire event
        notifier.onHitsplat(event(target, 14));
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreFriendly() {
        // init mocks
        Player target = mockPlayer();
        when(config.pkSkipFriendly()).thenReturn(true);
        when(target.isFriend()).thenReturn(true);

        // fire event
        notifier.onHitsplat(event(target, 15));
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // init mocks
        Player target = mockPlayer();
        when(config.notifyPk()).thenReturn(false);

        // fire event
        notifier.onHitsplat(event(target, 16));
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreOther() {
        // init mocks
        Player target = mockPlayer();

        // fire event
        HitsplatApplied event = new HitsplatApplied();
        event.setActor(target);
        event.setHitsplat(new HitsplatImpl(DAMAGE_OTHER, 17, 1));
        notifier.onHitsplat(event);
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreSelf() {
        // fire event
        HitsplatApplied event = new HitsplatApplied();
        event.setActor(localPlayer);
        event.setHitsplat(new HitsplatImpl(HitsplatID.DAMAGE_ME, 18, 1));
        notifier.onHitsplat(event);
        notifier.onTick();

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private Player mockPlayer() {
        Player target = mock(Player.class);
        when(target.getName()).thenReturn(TARGET);
        when(target.isDead()).thenReturn(true);
        when(target.getCombatLevel()).thenReturn(LEVEL);
        when(target.getWorldLocation()).thenReturn(LOCATION);
        when(target.getWorldView()).thenReturn(worldView);
        PlayerComposition comp = mock(PlayerComposition.class);
        when(target.getPlayerComposition()).thenReturn(comp);
        int[] equipment = new int[KitType.values().length];
        EQUIPMENT.forEach((kit, item) -> equipment[kit.getIndex()] = item.getId() + PlayerComposition.ITEM_OFFSET);
        when(comp.getEquipmentIds()).thenReturn(equipment);
        return target;
    }

    private static HitsplatApplied event(Actor actor, int amount) {
        HitsplatApplied e = new HitsplatApplied();
        e.setActor(actor);
        e.setHitsplat(new HitsplatImpl(HitsplatID.DAMAGE_ME, amount, 1));
        return e;
    }

    private PigeonProfile pkProfile(String name, boolean enabled, int minValue, boolean location,
                                    String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "pkEnabled", new JsonPrimitive(true),
                "pkSendImage", new JsonPrimitive(false),
                "pkSkipSafe", new JsonPrimitive(false),
                "pkSkipFriendly", new JsonPrimitive(false),
                "pkMinValue", new JsonPrimitive(minValue),
                "pkIncludeLocation", new JsonPrimitive(location),
                "pkNotifyMessage", new JsonPrimitive(message)
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

    static {
        Map<KitType, SerializedItemStack> m = new EnumMap<>(KitType.class);
        m.put(KitType.WEAPON, new SerializedItemStack(ItemID.STAFF_OF_ZAROS, 1, WEAPON_PRICE, "Ancient staff"));
        m.put(KitType.TORSO, new SerializedItemStack(ItemID.MYSTIC_ROBE_TOP, 1, TOP_PRICE, "Mystic robe top"));
        m.put(KitType.LEGS, new SerializedItemStack(ItemID.MYSTIC_ROBE_BOTTOM, 1, LEGS_PRICE, "Mystic robe bottom"));
        m.put(KitType.HANDS, new SerializedItemStack(ItemID.HUNDRED_GAUNTLETS_LEVEL_10, 1, HAND_PRICE, "Barrows gloves"));
        m.put(KitType.SHIELD, new SerializedItemStack(ItemID.ZAMORAKBOOK_COMPLETE, 1, SHIELD_PRICE, "Unholy book"));
        EQUIPMENT = Collections.unmodifiableMap(m);
    }
}
