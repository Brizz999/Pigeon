package pigeon.profiles;

import pigeon.PigeonConfig;
import pigeon.domain.AccountType;
import pigeon.domain.FilterMode;
import pigeon.domain.SeasonalPolicy;
import pigeon.util.ConfigUtil;
import pigeon.util.WorldUtils;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ProfileEligibilityService {
    private final Client client;

    @Inject
    public ProfileEligibilityService(Client client) {
        this.client = client;
    }

    public boolean isEligible(PigeonConfig config) {
        return isEligible(config, false);
    }

    public boolean isEligible(PigeonConfig config, boolean allowPvpArena) {
        return isEligible(config, allowPvpArena, false);
    }

    public boolean isEligible(PigeonConfig config, boolean allowPvpArena, boolean allowSeasonal) {
        return isEligible(config, allowPvpArena, allowSeasonal, false);
    }

    public boolean isEligible(PigeonConfig config, boolean allowPvpArena, boolean allowSeasonal,
                              boolean allowQuestSpeedrunning) {
        Player player = client.getLocalPlayer();
        AccountType accountType = AccountType.get(client.getVarbitValue(VarbitID.IRONMAN));
        if (player == null || player.getName() == null || accountType == null) {
            return false;
        }

        Set<WorldType> worldTypes = client.getWorldType();
        Set<WorldType> filteredWorldTypes = worldTypes;
        if (allowPvpArena && worldTypes.contains(WorldType.PVP_ARENA)) {
            EnumSet<WorldType> copy = EnumSet.noneOf(WorldType.class);
            copy.addAll(worldTypes);
            copy.remove(WorldType.PVP_ARENA);
            filteredWorldTypes = copy;
        }
        if (allowQuestSpeedrunning && filteredWorldTypes.contains(WorldType.QUEST_SPEEDRUNNING)) {
            EnumSet<WorldType> copy = EnumSet.noneOf(WorldType.class);
            copy.addAll(filteredWorldTypes);
            copy.remove(WorldType.QUEST_SPEEDRUNNING);
            filteredWorldTypes = copy;
        }
        SeasonalPolicy seasonalPolicy = config.seasonalPolicy();
        if (WorldUtils.isIgnoredWorld(filteredWorldTypes)) {
            return false;
        }
        if (!allowSeasonal && (WorldUtils.isSeasonalDeadman(worldTypes) || WorldUtils.isGridMaster(client))
            && seasonalPolicy == SeasonalPolicy.REJECT) {
            return false;
        }
        if (!allowSeasonal && worldTypes.contains(WorldType.SEASONAL) && seasonalPolicy == SeasonalPolicy.REJECT) {
            return false;
        }

        Set<String> filteredNames = ConfigUtil.readDelimited(config.filteredNames())
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        String playerName = player.getName().toLowerCase(Locale.ROOT);
        if (!filteredNames.isEmpty()
            && (config.nameFilterMode() == FilterMode.ALLOW) != filteredNames.contains(playerName)) {
            return false;
        }

        return config.nameFilterMode() == FilterMode.ALLOW
            || !config.deniedAccountTypes().contains(accountType);
    }
}
