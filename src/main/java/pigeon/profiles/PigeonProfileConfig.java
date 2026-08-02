package pigeon.profiles;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import pigeon.PigeonConfig;
import pigeon.domain.AccountType;
import pigeon.domain.ChatPrivacyMode;
import pigeon.domain.ChatNotificationType;
import pigeon.domain.FilterMode;
import pigeon.domain.ExceptionalDeath;
import pigeon.domain.PlayerLookupService;
import pigeon.domain.SeasonalPolicy;

import java.awt.Color;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Immutable configuration view for one profile delivery.
 *
 * <p>Settings are sparse: absent or malformed values inherit the current flat
 * configuration during migration. Webhook destinations never inherit, which
 * prevents one profile from accidentally sending to another profile's server.</p>
 */
public final class PigeonProfileConfig implements PigeonConfig {
    private static final Type ACCOUNT_TYPES = new TypeToken<Set<AccountType>>() { }.getType();
    private static final Type EXCEPTIONAL_DEATHS = new TypeToken<Set<ExceptionalDeath>>() { }.getType();
    private static final Type CHAT_TYPES = new TypeToken<Set<ChatNotificationType>>() { }.getType();

    private final Gson gson;
    private final PigeonProfile profile;
    private final PigeonConfig defaults;

    public PigeonProfileConfig(Gson gson, PigeonProfile profile, PigeonConfig defaults) {
        this.gson = gson;
        this.profile = profile;
        this.defaults = defaults;
    }

    public PigeonProfile getProfile() {
        return profile;
    }

    @Override
    public String primaryWebhook() {
        return join(profile.getWebhooks().getPrimary());
    }

    @Override
    public String questWebhook() {
        return override("questWebhook");
    }

    @Override
    public String petWebhook() {
        return override("petWebhook");
    }

    @Override
    public String levelWebhook() {
        return override("levelWebhook");
    }

    @Override
    public String killCountWebhook() {
        return override("killCountWebhook");
    }

    @Override
    public String lootWebhook() {
        return override("lootWebhook");
    }

    @Override
    public String pkWebhook() {
        return override("pkWebhook");
    }

    @Override
    public String slayerWebhook() {
        return override("slayerWebhook");
    }

    @Override
    public String deathWebhook() {
        return override("deathWebhook");
    }

    @Override
    public String collectionWebhook() {
        return override("collectionWebhook");
    }

    @Override
    public String diaryWebhook() {
        return override("diaryWebhook");
    }

    @Override
    public String combatTaskWebhook() {
        return override("combatTaskWebhook");
    }

    @Override
    public String clueWebhook() {
        return override("clueWebhook");
    }

    @Override
    public String tradeWebhook() {
        return override("tradeWebhook");
    }

    @Override
    public String grandExchangeWebhook() {
        return override("grandExchangeWebhook");
    }

    @Override
    public String groupStorageWebhook() {
        return override("groupStorageWebhook");
    }

    @Override
    public String gambleWebhook() {
        return override("gambleWebhook");
    }

    @Override
    public String speedrunWebhook() {
        return override("speedrunWebhook");
    }

    @Override
    public String chatWebhook() {
        return override("chatWebhook");
    }

    @Override
    public String metadataWebhook() {
        return override("metadataWebhook");
    }

    @Override
    public String externalWebhook() {
        return override("externalWebhook");
    }

    @Override
    public String leaguesWebhook() {
        return override("leaguesWebhook");
    }

    @Override
    public boolean notifyQuest() {
        return value("questEnabled", Boolean.class, defaults.notifyQuest());
    }

    @Override
    public boolean questSendImage() {
        return value("questSendImage", Boolean.class, defaults.questSendImage());
    }

    @Override
    public String questNotifyMessage() {
        return value("questNotifMessage", String.class, defaults.questNotifyMessage());
    }

    @Override
    public boolean notifyPet() {
        return value("petEnabled", Boolean.class, defaults.notifyPet());
    }

    @Override
    public boolean petSendImage() {
        return value("petSendImage", Boolean.class, defaults.petSendImage());
    }

    @Override
    public boolean petIncludeDuplicates() {
        return value("petIncludeDuplicates", Boolean.class, defaults.petIncludeDuplicates());
    }

    @Override
    public String petNotifyMessage() {
        return value("petNotifMessage", String.class, defaults.petNotifyMessage());
    }

    @Override
    public boolean notifyLevel() {
        return value("levelEnabled", Boolean.class, defaults.notifyLevel());
    }

    @Override
    public boolean levelSendImage() {
        return value("levelSendImage", Boolean.class, defaults.levelSendImage());
    }

    @Override
    public boolean levelNotifyVirtual() {
        return value("levelNotifyVirtual", Boolean.class, defaults.levelNotifyVirtual());
    }

    @Override
    public boolean levelNotifyCombat() {
        return value("levelNotifyCombat", Boolean.class, defaults.levelNotifyCombat());
    }

    @Override
    public int levelInterval() {
        return value("levelInterval", Integer.class, defaults.levelInterval());
    }

    @Override
    public int levelMinValue() {
        return value("levelMinValue", Integer.class, defaults.levelMinValue());
    }

    @Override
    public int levelMinScreenshotValue() {
        return value("levelMinScreenshotValue", Integer.class, defaults.levelMinScreenshotValue());
    }

    @Override
    public int levelIntervalOverride() {
        return value("levelIntervalOverride", Integer.class, defaults.levelIntervalOverride());
    }

    @Override
    public int xpInterval() {
        return value("xpInterval", Integer.class, defaults.xpInterval());
    }

    @Override
    public String levelNotifyMessage() {
        return value("levelNotifMessage", String.class, defaults.levelNotifyMessage());
    }

    @Override
    public boolean notifyKillCount() {
        return value("killCountEnabled", Boolean.class, defaults.notifyKillCount());
    }

    @Override
    public boolean killCountSendImage() {
        return value("killCountSendImage", Boolean.class, defaults.killCountSendImage());
    }

    @Override
    public boolean killCountNotifyInitial() {
        return value("killCountInitial", Boolean.class, defaults.killCountNotifyInitial());
    }

    @Override
    public boolean killCountNotifyBestTime() {
        return value("killCountPB", Boolean.class, defaults.killCountNotifyBestTime());
    }

    @Override
    public int killCountInterval() {
        return value("killCountInterval", Integer.class, defaults.killCountInterval());
    }

    @Override
    public boolean killCountPenanceQueen() {
        return value("killCountPenanceQueen", Boolean.class, defaults.killCountPenanceQueen());
    }

    @Override
    public String killCountMessage() {
        return value("killCountMessage", String.class, defaults.killCountMessage());
    }

    @Override
    public String killCountBestTimeMessage() {
        return value("killCountBestTimeMessage", String.class, defaults.killCountBestTimeMessage());
    }

    @Override
    public boolean notifyLoot() {
        return value("lootEnabled", Boolean.class, defaults.notifyLoot());
    }

    @Override
    public boolean lootSendImage() {
        return value("lootSendImage", Boolean.class, defaults.lootSendImage());
    }

    @Override
    public boolean lootIcons() {
        return value("lootIcons", Boolean.class, defaults.lootIcons());
    }

    @Override
    public int minLootValue() {
        return value("minLootValue", Integer.class, defaults.minLootValue());
    }

    @Override
    public int lootImageMinValue() {
        return value("lootImageMinValue", Integer.class, defaults.lootImageMinValue());
    }

    @Override
    public boolean includePlayerLoot() {
        return value("lootIncludePlayer", Boolean.class, defaults.includePlayerLoot());
    }

    @Override
    public boolean lootRedirectPlayerKill() {
        return value("lootRedirectPlayerKill", Boolean.class, defaults.lootRedirectPlayerKill());
    }

    @Override
    public boolean lootIncludeClueScrolls() {
        return value("lootIncludeClueScrolls", Boolean.class, defaults.lootIncludeClueScrolls());
    }

    @Override
    public boolean lootIncludeGambles() {
        return value("lootIncludeGambles", Boolean.class, defaults.lootIncludeGambles());
    }

    @Override
    public String lootItemAllowlist() {
        return value("lootItemAllowlist", String.class, defaults.lootItemAllowlist());
    }

    @Override
    public String lootItemDenylist() {
        return value("lootItemDenylist", String.class, defaults.lootItemDenylist());
    }

    @Override
    public String lootSourceDenylist() {
        return value("lootSourceDenylist", String.class, defaults.lootSourceDenylist());
    }

    @Override
    public int lootRarityThreshold() {
        return value("lootRarityThreshold", Integer.class, defaults.lootRarityThreshold());
    }

    @Override
    public boolean lootRarityValueIntersection() {
        return value("lootRarityValueIntersection", Boolean.class, defaults.lootRarityValueIntersection());
    }

    @Override
    public String lootNotifyMessage() {
        return value("lootNotifMessage", String.class, defaults.lootNotifyMessage());
    }

    @Override
    public boolean notifySlayer() {
        return value("slayerEnabled", Boolean.class, defaults.notifySlayer());
    }

    @Override
    public boolean slayerSendImage() {
        return value("slayerSendImage", Boolean.class, defaults.slayerSendImage());
    }

    @Override
    public int slayerPointThreshold() {
        return value("slayerPointThreshold", Integer.class, defaults.slayerPointThreshold());
    }

    @Override
    public String slayerNotifyMessage() {
        return value("slayerNotifMessage", String.class, defaults.slayerNotifyMessage());
    }

    @Override
    public boolean notifyDeath() {
        return value("deathEnabled", Boolean.class, defaults.notifyDeath());
    }

    @Override
    public boolean deathSendImage() {
        return value("deathSendImage", Boolean.class, defaults.deathSendImage());
    }

    @Override
    public boolean deathEmbedKeptItems() {
        return value("deathEmbedProtected", Boolean.class, defaults.deathEmbedKeptItems());
    }

    @Override
    public boolean deathIgnoreSafe() {
        return value("deathIgnoreSafe", Boolean.class, defaults.deathIgnoreSafe());
    }

    @Override
    public Set<ExceptionalDeath> deathSafeExceptions() {
        return value("deathSafeExceptions", EXCEPTIONAL_DEATHS, defaults.deathSafeExceptions());
    }

    @Override
    public String deathIgnoredRegions() {
        return value("deathIgnoredRegions", String.class, defaults.deathIgnoredRegions());
    }

    @Override
    public int deathMinValue() {
        return value("deathMinValue", Integer.class, defaults.deathMinValue());
    }

    @Override
    public String deathNotifyMessage() {
        return value("deathNotifMessage", String.class, defaults.deathNotifyMessage());
    }

    @Override
    public boolean deathNotifPvpEnabled() {
        return value("deathNotifPvpEnabled", Boolean.class, defaults.deathNotifPvpEnabled());
    }

    @Override
    public String deathNotifPvpMessage() {
        return value("deathNotifPvpMessage", String.class, defaults.deathNotifPvpMessage());
    }

    @Override
    public boolean notifyCollectionLog() {
        return value("collectionLogEnabled", Boolean.class, defaults.notifyCollectionLog());
    }

    @Override
    public boolean collectionSendImage() {
        return value("collectionSendImage", Boolean.class, defaults.collectionSendImage());
    }

    @Override
    public String collectionDenylist() {
        return value("collectionDenylist", String.class, defaults.collectionDenylist());
    }

    @Override
    public String collectionNotifyMessage() {
        return value("collectionNotifMessage", String.class, defaults.collectionNotifyMessage());
    }

    @Override
    public boolean notifyAchievementDiary() {
        return value("diaryEnabled", Boolean.class, defaults.notifyAchievementDiary());
    }

    @Override
    public boolean diarySendImage() {
        return value("diarySendImage", Boolean.class, defaults.diarySendImage());
    }

    @Override
    public pigeon.domain.AchievementDiary.Difficulty minDiaryDifficulty() {
        return value("diaryMinDifficulty", pigeon.domain.AchievementDiary.Difficulty.class,
            defaults.minDiaryDifficulty());
    }

    @Override
    public String diaryNotifyMessage() {
        return value("diaryMessage", String.class, defaults.diaryNotifyMessage());
    }

    @Override
    public boolean notifyCombatTask() {
        return value("combatTaskEnabled", Boolean.class, defaults.notifyCombatTask());
    }

    @Override
    public boolean combatTaskSendImage() {
        return value("combatTaskSendImage", Boolean.class, defaults.combatTaskSendImage());
    }

    @Override
    public pigeon.domain.CombatAchievementTier minCombatAchievementTier() {
        return value("combatTaskMinTier", pigeon.domain.CombatAchievementTier.class,
            defaults.minCombatAchievementTier());
    }

    @Override
    public String combatTaskMessage() {
        return value("combatTaskMessage", String.class, defaults.combatTaskMessage());
    }

    @Override
    public String combatTaskUnlockMessage() {
        return value("combatTaskUnlockMessage", String.class, defaults.combatTaskUnlockMessage());
    }

    @Override
    public boolean notifyClue() {
        return value("clueEnabled", Boolean.class, defaults.notifyClue());
    }

    @Override
    public boolean clueSendImage() {
        return value("clueSendImage", Boolean.class, defaults.clueSendImage());
    }

    @Override
    public boolean clueShowItems() {
        return value("clueShowItems", Boolean.class, defaults.clueShowItems());
    }

    @Override
    public pigeon.domain.ClueTier clueMinTier() {
        return value("clueMinTier", pigeon.domain.ClueTier.class, defaults.clueMinTier());
    }

    @Override
    public int clueMinValue() {
        return value("clueMinValue", Integer.class, defaults.clueMinValue());
    }

    @Override
    public int clueImageMinValue() {
        return value("clueImageMinValue", Integer.class, defaults.clueImageMinValue());
    }

    @Override
    public String clueNotifyMessage() {
        return value("clueNotifMessage", String.class, defaults.clueNotifyMessage());
    }

    @Override
    public boolean notifyPk() {
        return value("pkEnabled", Boolean.class, defaults.notifyPk());
    }

    @Override
    public boolean pkSendImage() {
        return value("pkSendImage", Boolean.class, defaults.pkSendImage());
    }

    @Override
    public boolean pkSkipSafe() {
        return value("pkSkipSafe", Boolean.class, defaults.pkSkipSafe());
    }

    @Override
    public boolean pkSkipFriendly() {
        return value("pkSkipFriendly", Boolean.class, defaults.pkSkipFriendly());
    }

    @Override
    public int pkMinValue() {
        return value("pkMinValue", Integer.class, defaults.pkMinValue());
    }

    @Override
    public boolean pkIncludeLocation() {
        return value("pkIncludeLocation", Boolean.class, defaults.pkIncludeLocation());
    }

    @Override
    public String pkNotifyMessage() {
        return value("pkNotifyMessage", String.class, defaults.pkNotifyMessage());
    }

    @Override
    public boolean notifyTrades() {
        return value("notifyTrades", Boolean.class, defaults.notifyTrades());
    }

    @Override
    public boolean tradeSendImage() {
        return value("tradeSendImage", Boolean.class, defaults.tradeSendImage());
    }

    @Override
    public int tradeMinValue() {
        return value("tradeMinValue", Integer.class, defaults.tradeMinValue());
    }

    @Override
    public String tradeNotifyMessage() {
        return value("tradeNotifyMessage", String.class, defaults.tradeNotifyMessage());
    }

    @Override
    public boolean notifyGrandExchange() {
        return value("notifyGrandExchange", Boolean.class, defaults.notifyGrandExchange());
    }

    @Override
    public boolean grandExchangeSendImage() {
        return value("grandExchangeSendImage", Boolean.class, defaults.grandExchangeSendImage());
    }

    @Override
    public boolean grandExchangeIncludeCancelled() {
        return value("grandExchangeIncludeCancelled", Boolean.class, defaults.grandExchangeIncludeCancelled());
    }

    @Override
    public int grandExchangeMinValue() {
        return value("grandExchangeMinValue", Integer.class, defaults.grandExchangeMinValue());
    }

    @Override
    public int grandExchangeProgressSpacingMinutes() {
        return value("grandExchangeProgressSpacingMinutes", Integer.class,
            defaults.grandExchangeProgressSpacingMinutes());
    }

    @Override
    public String grandExchangeNotifyMessage() {
        return value("grandExchangeNotifyMessage", String.class, defaults.grandExchangeNotifyMessage());
    }

    @Override
    public boolean notifyGroupStorage() {
        return value("groupStorageEnabled", Boolean.class, defaults.notifyGroupStorage());
    }

    @Override
    public boolean groupStorageSendImage() {
        return value("groupStorageSendImage", Boolean.class, defaults.groupStorageSendImage());
    }

    @Override
    public int groupStorageMinValue() {
        return value("groupStorageMinValue", Integer.class, defaults.groupStorageMinValue());
    }

    @Override
    public boolean groupStorageIncludeClan() {
        return value("groupStorageIncludeClan", Boolean.class, defaults.groupStorageIncludeClan());
    }

    @Override
    public boolean groupStorageIncludePrice() {
        return value("groupStorageIncludePrice", Boolean.class, defaults.groupStorageIncludePrice());
    }

    @Override
    public String groupStorageNotifyMessage() {
        return value("groupStorageNotifyMessage", String.class, defaults.groupStorageNotifyMessage());
    }

    @Override
    public boolean notifyGamble() {
        return value("gambleEnabled", Boolean.class, defaults.notifyGamble());
    }

    @Override
    public boolean gambleSendImage() {
        return value("gambleSendImage", Boolean.class, defaults.gambleSendImage());
    }

    @Override
    public int gambleInterval() {
        return value("gambleInterval", Integer.class, defaults.gambleInterval());
    }

    @Override
    public boolean gambleRareLoot() {
        return value("gambleRareLoot", Boolean.class, defaults.gambleRareLoot());
    }

    @Override
    public String gambleNotifyMessage() {
        return value("gambleNotifMessage", String.class, defaults.gambleNotifyMessage());
    }

    @Override
    public String gambleRareNotifyMessage() {
        return value("gambleRareNotifMessage", String.class, defaults.gambleRareNotifyMessage());
    }

    @Override
    public boolean notifyLeagues() {
        return value("notifyLeagues", Boolean.class, defaults.notifyLeagues());
    }

    @Override
    public boolean leaguesSendImage() {
        return value("leaguesSendImage", Boolean.class, defaults.leaguesSendImage());
    }

    @Override
    public boolean leaguesAreaUnlock() {
        return value("leaguesAreaUnlock", Boolean.class, defaults.leaguesAreaUnlock());
    }

    @Override
    public boolean leaguesRelicUnlock() {
        return value("leaguesRelicUnlock", Boolean.class, defaults.leaguesRelicUnlock());
    }

    @Override
    public boolean leaguesTaskCompletion() {
        return value("leaguesTaskCompletion", Boolean.class, defaults.leaguesTaskCompletion());
    }

    @Override
    public boolean leaguesMasteryUnlock() {
        return value("leaguesMasteryUnlock", Boolean.class, defaults.leaguesMasteryUnlock());
    }

    @Override
    public pigeon.domain.LeagueTaskDifficulty leaguesTaskMinTier() {
        return value("leaguesTaskMinTier", pigeon.domain.LeagueTaskDifficulty.class,
            defaults.leaguesTaskMinTier());
    }

    @Override
    public boolean notifySpeedrun() {
        return value("speedrunEnabled", Boolean.class, defaults.notifySpeedrun());
    }

    @Override
    public boolean speedrunSendImage() {
        return value("speedrunSendImage", Boolean.class, defaults.speedrunSendImage());
    }

    @Override
    public boolean speedrunPBOnly() {
        return value("speedrunPBOnly", Boolean.class, defaults.speedrunPBOnly());
    }

    @Override
    public String speedrunPBMessage() {
        return value("speedrunPBMessage", String.class, defaults.speedrunPBMessage());
    }

    @Override
    public String speedrunMessage() {
        return value("speedrunMessage", String.class, defaults.speedrunMessage());
    }

    @Override
    public boolean notifyChat() {
        return value("notifyChat", Boolean.class, defaults.notifyChat());
    }

    @Override
    public boolean chatSendImage() {
        return value("chatSendImage", Boolean.class, defaults.chatSendImage());
    }

    @Override
    public Set<ChatNotificationType> chatMessageTypes() {
        return value("chatMessageTypes", CHAT_TYPES, defaults.chatMessageTypes());
    }

    @Override
    public String chatPatterns() {
        return value("chatPatterns", String.class, defaults.chatPatterns());
    }

    @Override
    public String chatNotifyMessage() {
        return value("chatNotifyMessage", String.class, defaults.chatNotifyMessage());
    }

    @Override
    public boolean notifyExternal() {
        return value("notifyExternal", Boolean.class, defaults.notifyExternal());
    }

    @Override
    public pigeon.domain.ExternalScreenshotPolicy externalSendImage() {
        return value("externalSendImage", pigeon.domain.ExternalScreenshotPolicy.class,
            defaults.externalSendImage());
    }

    @Override
    public int maxRetries() {
        return value("maxRetries", Integer.class, defaults.maxRetries());
    }

    @Override
    public int baseRetryDelay() {
        return value("baseRetryDelay", Integer.class, defaults.baseRetryDelay());
    }

    @Override
    public int screenshotScale() {
        return value("screenshotScale", Integer.class, defaults.screenshotScale());
    }

    @Override
    public boolean discordRichEmbeds() {
        return value("discordRichEmbeds", Boolean.class, defaults.discordRichEmbeds());
    }

    @Override
    public String embedFooterText() {
        return value("embedFooterText", String.class, defaults.embedFooterText());
    }

    @Override
    public String embedFooterIcon() {
        return value("embedFooterIcon", String.class, defaults.embedFooterIcon());
    }

    @Override
    public String filteredNames() {
        return value("ignoredNames", String.class, defaults.filteredNames());
    }

    @Override
    public FilterMode nameFilterMode() {
        return value("nameFilterMode", FilterMode.class, defaults.nameFilterMode());
    }

    @Override
    public void setNameFilterMode(FilterMode filterMode) {
        throw new UnsupportedOperationException("Profile configuration snapshots are immutable");
    }

    @Override
    public PlayerLookupService playerLookupService() {
        return value("playerLookupService", PlayerLookupService.class, defaults.playerLookupService());
    }

    @Override
    public ChatPrivacyMode chatPrivacy() {
        return value("chatPrivacy", ChatPrivacyMode.class, defaults.chatPrivacy());
    }

    @Override
    public void setChatPrivacy(ChatPrivacyMode mode) {
        throw new UnsupportedOperationException("Profile configuration snapshots are immutable");
    }

    @Override
    public boolean sendDiscordUser() {
        return value("sendDiscordUser", Boolean.class, defaults.sendDiscordUser());
    }

    @Override
    public boolean sendClanName() {
        return value("sendClanName", Boolean.class, defaults.sendClanName());
    }

    @Override
    public boolean sendGroupIronClanName() {
        return value("sendGroupIronClanName", Boolean.class, defaults.sendGroupIronClanName());
    }

    @Override
    public String threadNameTemplate() {
        return value("threadNameTemplate", String.class, defaults.threadNameTemplate());
    }

    @Override
    public String screenshotFilenameTemplate() {
        return value("screenshotFilenameTemplate", String.class, defaults.screenshotFilenameTemplate());
    }

    @Override
    public SeasonalPolicy seasonalPolicy() {
        return value("seasonalPolicy", SeasonalPolicy.class, defaults.seasonalPolicy());
    }

    @Override
    public void setSeasonalPolicy(SeasonalPolicy policy) {
        throw new UnsupportedOperationException("Profile configuration snapshots are immutable");
    }

    @Override
    public boolean includeLocation() {
        return value("includeLocation", Boolean.class, defaults.includeLocation());
    }

    @Override
    public boolean includeClientFrame() {
        return value("includeClientFrame", Boolean.class, defaults.includeClientFrame());
    }

    @Override
    public Color embedColor() {
        JsonElement element = profile.getSettings().get("embedColor");
        if (element instanceof JsonPrimitive && ((JsonPrimitive) element).isNumber()) {
            return new Color(element.getAsInt(), true);
        }
        return defaults.embedColor();
    }

    @Override
    public Set<AccountType> deniedAccountTypes() {
        return value("deniedAccountTypes", ACCOUNT_TYPES, defaults.deniedAccountTypes());
    }

    @Override
    public String customPlayerBadge() {
        return value("customPlayerBadge", String.class, defaults.customPlayerBadge());
    }

    private String override(String key) {
        List<String> urls = profile.getWebhooks().getOverrides().get(key);
        return urls == null ? "" : join(urls);
    }

    private static String join(List<String> urls) {
        return String.join("\n", urls);
    }

    private <T> T value(String key, Class<T> type, T fallback) {
        JsonElement element = profile.getSettings().get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (type == Boolean.class && (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())) {
            return fallback;
        }
        if (type == Integer.class && (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())) {
            return fallback;
        }
        if ((type == String.class || type.isEnum())
            && (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())) {
            return fallback;
        }
        return value(key, (Type) type, fallback);
    }

    private <T> T value(String key, Type type, T fallback) {
        JsonElement element = profile.getSettings().get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            T parsed = gson.fromJson(element, type);
            return parsed == null ? fallback : parsed;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
