package pigeon.profiles;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import pigeon.PigeonConfig;
import pigeon.domain.SeasonalPolicy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PigeonProfileConfigTest {
    private final PigeonConfig defaults = mock(PigeonConfig.class);

    @Test
    void readsSparseTypedSettingsAndStructuredWebhooks() {
        when(defaults.notifyQuest()).thenReturn(false);
        when(defaults.notifyPet()).thenReturn(false);
        when(defaults.questSendImage()).thenReturn(true);
        when(defaults.questNotifyMessage()).thenReturn("default");
        when(defaults.seasonalPolicy()).thenReturn(SeasonalPolicy.REJECT);
        when(defaults.primaryWebhook()).thenReturn("https://should-not-leak.example");
        when(defaults.notifyLevel()).thenReturn(false);
        when(defaults.levelInterval()).thenReturn(10);
        when(defaults.notifyKillCount()).thenReturn(false);
        when(defaults.killCountInterval()).thenReturn(50);
        when(defaults.notifyLoot()).thenReturn(false);
        when(defaults.minLootValue()).thenReturn(1_000_000);
        when(defaults.notifySlayer()).thenReturn(false);
        when(defaults.slayerPointThreshold()).thenReturn(100);
        when(defaults.notifyDeath()).thenReturn(false);
        when(defaults.deathSafeExceptions()).thenReturn(java.util.EnumSet.noneOf(pigeon.domain.ExceptionalDeath.class));
        when(defaults.notifyCollectionLog()).thenReturn(false);
        when(defaults.notifyAchievementDiary()).thenReturn(false);
        when(defaults.notifyCombatTask()).thenReturn(false);
        when(defaults.notifyClue()).thenReturn(false);
        when(defaults.notifyPk()).thenReturn(false);
        when(defaults.notifyTrades()).thenReturn(false);
        when(defaults.notifyGrandExchange()).thenReturn(false);
        when(defaults.notifyGroupStorage()).thenReturn(false);
        when(defaults.notifyGamble()).thenReturn(false);
        when(defaults.notifyLeagues()).thenReturn(false);
        when(defaults.notifySpeedrun()).thenReturn(false);
        when(defaults.notifyChat()).thenReturn(false);
        when(defaults.notifyExternal()).thenReturn(false);

        PigeonProfile profile = PigeonProfile.create("Clan").toBuilder()
            .settings(Map.ofEntries(
                Map.entry("questEnabled", new JsonPrimitive(true)),
                Map.entry("petEnabled", new JsonPrimitive(true)),
                Map.entry("petIncludeDuplicates", new JsonPrimitive(false)),
                Map.entry("questSendImage", new JsonPrimitive(false)),
                Map.entry("questNotifMessage", new JsonPrimitive("profile message")),
                Map.entry("levelEnabled", new JsonPrimitive(true)),
                Map.entry("levelInterval", new JsonPrimitive(5)),
                Map.entry("killCountEnabled", new JsonPrimitive(true)),
                Map.entry("killCountInterval", new JsonPrimitive(25)),
                Map.entry("lootEnabled", new JsonPrimitive(true)),
                Map.entry("minLootValue", new JsonPrimitive(500_000)),
                Map.entry("slayerEnabled", new JsonPrimitive(true)),
                Map.entry("slayerPointThreshold", new JsonPrimitive(25)),
                Map.entry("deathEnabled", new JsonPrimitive(true)),
                Map.entry("deathMinValue", new JsonPrimitive(10_000)),
                Map.entry("collectionLogEnabled", new JsonPrimitive(true)),
                Map.entry("diaryEnabled", new JsonPrimitive(true)),
                Map.entry("diaryMinDifficulty", new JsonPrimitive("HARD")),
                Map.entry("combatTaskEnabled", new JsonPrimitive(true)),
                Map.entry("combatTaskMinTier", new JsonPrimitive("ELITE")),
                Map.entry("clueEnabled", new JsonPrimitive(true)),
                Map.entry("clueMinTier", new JsonPrimitive("HARD")),
                Map.entry("pkEnabled", new JsonPrimitive(true)),
                Map.entry("pkMinValue", new JsonPrimitive(250_000)),
                Map.entry("notifyTrades", new JsonPrimitive(true)),
                Map.entry("tradeMinValue", new JsonPrimitive(100_000)),
                Map.entry("notifyGrandExchange", new JsonPrimitive(true)),
                Map.entry("grandExchangeMinValue", new JsonPrimitive(750_000)),
                Map.entry("grandExchangeProgressSpacingMinutes", new JsonPrimitive(5)),
                Map.entry("groupStorageEnabled", new JsonPrimitive(true)),
                Map.entry("groupStorageMinValue", new JsonPrimitive(400_000)),
                Map.entry("groupStorageIncludePrice", new JsonPrimitive(false)),
                Map.entry("gambleEnabled", new JsonPrimitive(true)),
                Map.entry("gambleInterval", new JsonPrimitive(25)),
                Map.entry("notifyLeagues", new JsonPrimitive(true)),
                Map.entry("leaguesTaskMinTier", new JsonPrimitive("MASTER")),
                Map.entry("speedrunEnabled", new JsonPrimitive(true)),
                Map.entry("speedrunPBOnly", new JsonPrimitive(false)),
                Map.entry("notifyChat", new JsonPrimitive(true)),
                Map.entry("chatMessageTypes", new Gson().toJsonTree(java.util.EnumSet.of(
                    pigeon.domain.ChatNotificationType.GAME, pigeon.domain.ChatNotificationType.CLAN))),
                Map.entry("notifyExternal", new JsonPrimitive(true)),
                Map.entry("externalSendImage", new JsonPrimitive("ALWAYS")),
                Map.entry("seasonalPolicy", new JsonPrimitive("ACCEPT"))
            ))
            .webhooks(new ProfileWebhooks(
                List.of("https://example.com/primary"),
                Map.ofEntries(
                    Map.entry("questWebhook", List.of("https://example.com/quests")),
                    Map.entry("petWebhook", List.of("https://example.com/pets")),
                    Map.entry("levelWebhook", List.of("https://example.com/levels")),
                    Map.entry("killCountWebhook", List.of("https://example.com/kills")),
                    Map.entry("lootWebhook", List.of("https://example.com/loot")),
                    Map.entry("pkWebhook", List.of("https://example.com/pk")),
                    Map.entry("slayerWebhook", List.of("https://example.com/slayer")),
                    Map.entry("deathWebhook", List.of("https://example.com/death")),
                    Map.entry("collectionWebhook", List.of("https://example.com/collection")),
                    Map.entry("diaryWebhook", List.of("https://example.com/diary")),
                    Map.entry("combatTaskWebhook", List.of("https://example.com/combat")),
                    Map.entry("clueWebhook", List.of("https://example.com/clue")),
                    Map.entry("tradeWebhook", List.of("https://example.com/trade")),
                    Map.entry("grandExchangeWebhook", List.of("https://example.com/ge")),
                    Map.entry("groupStorageWebhook", List.of("https://example.com/storage")),
                    Map.entry("gambleWebhook", List.of("https://example.com/gamble")),
                    Map.entry("leaguesWebhook", List.of("https://example.com/leagues")),
                    Map.entry("speedrunWebhook", List.of("https://example.com/speedrun")),
                    Map.entry("chatWebhook", List.of("https://example.com/chat")),
                    Map.entry("metadataWebhook", List.of("https://example.com/metadata")),
                    Map.entry("externalWebhook", List.of("https://example.com/external"))
                )
            ))
            .build();

        PigeonProfileConfig config = new PigeonProfileConfig(new Gson(), profile, defaults);

        assertTrue(config.notifyQuest());
        assertTrue(config.notifyPet());
        assertFalse(config.petIncludeDuplicates());
        assertEquals("https://example.com/pets", config.petWebhook());
        assertFalse(config.questSendImage());
        assertEquals("profile message", config.questNotifyMessage());
        assertEquals(SeasonalPolicy.ACCEPT, config.seasonalPolicy());
        assertEquals("https://example.com/primary", config.primaryWebhook());
        assertEquals("https://example.com/quests", config.questWebhook());
        assertTrue(config.notifyLevel());
        assertEquals(5, config.levelInterval());
        assertEquals("https://example.com/levels", config.levelWebhook());
        assertTrue(config.notifyKillCount());
        assertEquals(25, config.killCountInterval());
        assertEquals("https://example.com/kills", config.killCountWebhook());
        assertTrue(config.notifyLoot());
        assertEquals(500_000, config.minLootValue());
        assertEquals("https://example.com/loot", config.lootWebhook());
        assertEquals("https://example.com/pk", config.pkWebhook());
        assertTrue(config.notifySlayer());
        assertEquals(25, config.slayerPointThreshold());
        assertEquals("https://example.com/slayer", config.slayerWebhook());
        assertTrue(config.notifyDeath());
        assertEquals(10_000, config.deathMinValue());
        assertEquals("https://example.com/death", config.deathWebhook());
        assertTrue(config.notifyCollectionLog());
        assertEquals("https://example.com/collection", config.collectionWebhook());
        assertTrue(config.notifyAchievementDiary());
        assertEquals(pigeon.domain.AchievementDiary.Difficulty.HARD, config.minDiaryDifficulty());
        assertEquals("https://example.com/diary", config.diaryWebhook());
        assertTrue(config.notifyCombatTask());
        assertEquals(pigeon.domain.CombatAchievementTier.ELITE, config.minCombatAchievementTier());
        assertEquals("https://example.com/combat", config.combatTaskWebhook());
        assertTrue(config.notifyClue());
        assertEquals(pigeon.domain.ClueTier.HARD, config.clueMinTier());
        assertEquals("https://example.com/clue", config.clueWebhook());
        assertTrue(config.notifyPk());
        assertEquals(250_000, config.pkMinValue());
        assertTrue(config.notifyTrades());
        assertEquals(100_000, config.tradeMinValue());
        assertEquals("https://example.com/trade", config.tradeWebhook());
        assertTrue(config.notifyGrandExchange());
        assertEquals(750_000, config.grandExchangeMinValue());
        assertEquals(5, config.grandExchangeProgressSpacingMinutes());
        assertEquals("https://example.com/ge", config.grandExchangeWebhook());
        assertTrue(config.notifyGroupStorage());
        assertEquals(400_000, config.groupStorageMinValue());
        assertFalse(config.groupStorageIncludePrice());
        assertEquals("https://example.com/storage", config.groupStorageWebhook());
        assertTrue(config.notifyGamble());
        assertEquals(25, config.gambleInterval());
        assertEquals("https://example.com/gamble", config.gambleWebhook());
        assertTrue(config.notifyLeagues());
        assertEquals(pigeon.domain.LeagueTaskDifficulty.MASTER, config.leaguesTaskMinTier());
        assertEquals("https://example.com/leagues", config.leaguesWebhook());
        assertTrue(config.notifySpeedrun());
        assertFalse(config.speedrunPBOnly());
        assertEquals("https://example.com/speedrun", config.speedrunWebhook());
        assertTrue(config.notifyChat());
        assertEquals(java.util.EnumSet.of(pigeon.domain.ChatNotificationType.GAME,
            pigeon.domain.ChatNotificationType.CLAN), config.chatMessageTypes());
        assertEquals("https://example.com/chat", config.chatWebhook());
        assertEquals("https://example.com/metadata", config.metadataWebhook());
        assertTrue(config.notifyExternal());
        assertEquals(pigeon.domain.ExternalScreenshotPolicy.ALWAYS, config.externalSendImage());
        assertEquals("https://example.com/external", config.externalWebhook());
    }

    @Test
    void neverInheritsWebhookCredentials() {
        when(defaults.primaryWebhook()).thenReturn("https://discord.com/api/webhooks/global/secret");
        when(defaults.questWebhook()).thenReturn("https://discord.com/api/webhooks/global/quest-secret");

        PigeonProfileConfig config = new PigeonProfileConfig(
            new Gson(),
            PigeonProfile.create("Safe"),
            defaults
        );

        assertEquals("", config.primaryWebhook());
        assertEquals("", config.questWebhook());
        assertEquals("", config.petWebhook());
        assertEquals("", config.levelWebhook());
        assertEquals("", config.killCountWebhook());
        assertEquals("", config.lootWebhook());
        assertEquals("", config.pkWebhook());
        assertEquals("", config.slayerWebhook());
        assertEquals("", config.deathWebhook());
        assertEquals("", config.collectionWebhook());
        assertEquals("", config.diaryWebhook());
        assertEquals("", config.combatTaskWebhook());
        assertEquals("", config.clueWebhook());
        assertEquals("", config.tradeWebhook());
        assertEquals("", config.grandExchangeWebhook());
        assertEquals("", config.groupStorageWebhook());
        assertEquals("", config.gambleWebhook());
        assertEquals("", config.leaguesWebhook());
        assertEquals("", config.speedrunWebhook());
        assertEquals("", config.chatWebhook());
        assertEquals("", config.metadataWebhook());
        assertEquals("", config.externalWebhook());
    }

    @Test
    void malformedSettingFallsBackWithoutBreakingProfile() {
        when(defaults.notifyQuest()).thenReturn(true);
        PigeonProfile profile = PigeonProfile.create("Clan").toBuilder()
            .settings(Map.of("questEnabled", new JsonPrimitive("not-a-boolean")))
            .build();

        PigeonProfileConfig config = new PigeonProfileConfig(new Gson(), profile, defaults);

        assertTrue(config.notifyQuest());
    }
}
