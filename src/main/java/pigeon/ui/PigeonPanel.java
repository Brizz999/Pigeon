package pigeon.ui;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import pigeon.PigeonConfig;
import pigeon.domain.ExceptionalDeath;
import pigeon.domain.AchievementDiary;
import pigeon.domain.CombatAchievementTier;
import pigeon.domain.ClueTier;
import pigeon.domain.LeagueTaskDifficulty;
import pigeon.domain.ChatNotificationType;
import pigeon.domain.ExternalScreenshotPolicy;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileCodec;
import pigeon.profiles.ProfileService;
import pigeon.profiles.ProfileRouteOverlap;
import pigeon.profiles.ProfileValidationException;
import pigeon.profiles.ProfileWebhooks;
import pigeon.util.Utils;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PigeonPanel extends PluginPanel {
    private static final int GAP = 8;

    private final ProfileService profileService;
    private final Gson gson;
    private final PigeonConfig defaults;
    private final JPanel profileList = new JPanel();

    @Inject
    public PigeonPanel(ProfileService profileService, Gson gson, PigeonConfig defaults) {
        this.profileService = profileService;
        this.gson = gson;
        this.defaults = defaults;

        setLayout(new BorderLayout(GAP, GAP));
        setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));

        JLabel title = new JLabel("Pigeon profiles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setToolTipText("Every enabled profile evaluates and routes notifications independently.");
        JLabel subtitle = new JLabel("Independent routing per profile");
        subtitle.setToolTipText("Each enabled profile evaluates and routes notifications independently.");
        subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        subtitle.setFont(subtitle.getFont().deriveFont(11f));

        JButton createButton = iconButton(PigeonActionIcon.Type.ADD, "Create profile",
            ColorScheme.PROGRESS_COMPLETE_COLOR,
            this::createProfile);
        JButton importButton = iconButton(PigeonActionIcon.Type.IMPORT, "Import profile from clipboard",
            ColorScheme.PROGRESS_COMPLETE_COLOR, this::importProfile);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(createButton);
        actions.add(importButton);

        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        heading.add(title);
        heading.add(subtitle);

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.add(heading, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        profileList.setLayout(new BoxLayout(profileList, BoxLayout.Y_AXIS));
        add(profileList, BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        profileList.removeAll();
        List<PigeonProfile> profiles = profileService.list();
        List<ProfileRouteOverlap> overlaps = profileService.findOverlappingRoutes();
        if (!overlaps.isEmpty()) {
            String warning = overlaps.stream()
                .map(overlap -> escapeHtml(overlap.getEndpoint()) + " ("
                    + overlap.getProfileNames().stream().map(this::escapeHtml)
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("; "));
            JLabel overlapWarning = new JLabel(
                "<html><b>Overlapping routes:</b> " + warning
                    + ". One event may intentionally produce multiple messages.</html>");
            overlapWarning.setToolTipText(
                "Only endpoint hosts are shown; webhook credentials remain hidden.");
            overlapWarning.setAlignmentX(Component.LEFT_ALIGNMENT);
            profileList.add(overlapWarning);
            profileList.add(Box.createRigidArea(new Dimension(0, GAP)));
        }
        if (profiles.isEmpty()) {
            JLabel empty = new JLabel("No profiles yet. Create or import one to begin.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            profileList.add(empty);
        } else {
            for (PigeonProfile profile : profiles) {
                profileList.add(profileCard(profile));
                profileList.add(Box.createRigidArea(new Dimension(0, GAP)));
            }
        }
        profileList.revalidate();
        profileList.repaint();
    }

    private JPanel profileCard(PigeonProfile profile) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JCheckBox enabled = new JCheckBox(profile.getName(), profile.isEnabled());
        enabled.setToolTipText("Enable this profile alongside any other enabled profiles.");
        enabled.addActionListener(event -> runAction(() ->
            profileService.setEnabled(profile.getId(), enabled.isSelected())
        ));
        card.add(enabled, BorderLayout.NORTH);

        JLabel summary = new JLabel(profileSummary(profile));
        summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        summary.setFont(summary.getFont().deriveFont(11f));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttons.add(iconButton(PigeonActionIcon.Type.CONFIGURE, "Configure profile",
            ColorScheme.LIGHT_GRAY_COLOR,
            () -> configureProfile(profile)));
        buttons.add(iconButton(PigeonActionIcon.Type.EXPORT, "Export profile to clipboard",
            ColorScheme.PROGRESS_COMPLETE_COLOR, () -> exportProfile(profile)));
        buttons.add(iconButton(PigeonActionIcon.Type.CLONE, "Clone profile", ColorScheme.LIGHT_GRAY_COLOR,
            () -> cloneProfile(profile)));
        buttons.add(iconButton(PigeonActionIcon.Type.DELETE, "Delete profile", ColorScheme.PROGRESS_ERROR_COLOR,
            () -> deleteProfile(profile)));
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(summary);
        details.add(buttons);
        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private String profileSummary(PigeonProfile profile) {
        int primaryRoutes = profile.getWebhooks().getPrimary().size();
        int overrideRoutes = profile.getWebhooks().getOverrides().values().stream()
            .mapToInt(List::size)
            .sum();
        long enabledRules = profile.getSettings().entrySet().stream()
            .filter(entry -> (entry.getKey().endsWith("Enabled") || entry.getKey().startsWith("notify"))
                && entry.getValue().isJsonPrimitive()
                && entry.getValue().getAsJsonPrimitive().isBoolean()
                && entry.getValue().getAsBoolean())
            .count();
        return primaryRoutes + " primary · " + overrideRoutes + " overrides · "
            + enabledRules + " rules enabled";
    }

    private JButton iconButton(PigeonActionIcon.Type icon, String tooltip, java.awt.Color color, Runnable action) {
        JButton button = new JButton(new PigeonActionIcon(icon, color));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setForeground(color);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
            BorderFactory.createEmptyBorder(1, 1, 1, 1)
        ));
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        Dimension size = new Dimension(30, 30);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.getModel().addChangeListener(event -> button.setBackground(
            button.getModel().isRollover()
                ? ColorScheme.DARK_GRAY_HOVER_COLOR
                : ColorScheme.DARKER_GRAY_COLOR
        ));
        button.addActionListener(event -> action.run());
        return button;
    }

    private void createProfile() {
        String name = promptForName("Create profile", "");
        if (name != null) {
            runAction(() -> profileService.create(name));
        }
    }

    private void cloneProfile(PigeonProfile profile) {
        String name = promptForName("Clone profile", profile.getName() + " copy");
        if (name != null) {
            runAction(() -> profileService.cloneProfile(profile.getId(), name));
        }
    }

    private void configureProfile(PigeonProfile profile) {
        PigeonProfileConfig view = new PigeonProfileConfig(gson, profile, defaults);

        JTextField profileName = new JTextField(profile.getName());
        JTextArea primaryWebhooks = textArea(String.join("\n", profile.getWebhooks().getPrimary()), 3);
        JTextArea questWebhook = textArea(view.questWebhook(), 2);
        JTextArea petWebhook = textArea(view.petWebhook(), 2);
        JCheckBox petEnabled = new JCheckBox("Enable Pet notifications", view.notifyPet());
        JCheckBox petScreenshot = new JCheckBox("Include Pet screenshots", view.petSendImage());
        JCheckBox petDuplicates = new JCheckBox("Include duplicate pets", view.petIncludeDuplicates());
        JTextField petMessage = new JTextField(view.petNotifyMessage());
        JCheckBox questEnabled = new JCheckBox("Enable Quest notifications", view.notifyQuest());
        JCheckBox questScreenshot = new JCheckBox("Include Quest screenshot", view.questSendImage());
        JTextField questMessage = new JTextField(view.questNotifyMessage());
        JTextArea levelWebhook = textArea(view.levelWebhook(), 2);
        JCheckBox levelEnabled = new JCheckBox("Enable Level and XP notifications", view.notifyLevel());
        JCheckBox levelScreenshot = new JCheckBox("Include Level screenshots", view.levelSendImage());
        JCheckBox virtualLevels = new JCheckBox("Notify on virtual levels", view.levelNotifyVirtual());
        JCheckBox combatLevels = new JCheckBox("Notify on combat levels", view.levelNotifyCombat());
        JTextField levelInterval = new JTextField(String.valueOf(view.levelInterval()));
        JTextField minimumLevel = new JTextField(String.valueOf(view.levelMinValue()));
        JTextField screenshotLevel = new JTextField(String.valueOf(view.levelMinScreenshotValue()));
        JTextField intervalOverride = new JTextField(String.valueOf(view.levelIntervalOverride()));
        JTextField xpInterval = new JTextField(String.valueOf(view.xpInterval()));
        JTextField levelMessage = new JTextField(view.levelNotifyMessage());
        JTextArea killCountWebhook = textArea(view.killCountWebhook(), 2);
        JCheckBox killCountEnabled = new JCheckBox("Enable Kill Count notifications", view.notifyKillCount());
        JCheckBox killCountScreenshot = new JCheckBox("Include Kill Count screenshots", view.killCountSendImage());
        JCheckBox initialKill = new JCheckBox("Notify on the first kill", view.killCountNotifyInitial());
        JCheckBox personalBest = new JCheckBox("Notify on personal bests", view.killCountNotifyBestTime());
        JCheckBox penanceQueen = new JCheckBox("Notify on Penance Queen kills", view.killCountPenanceQueen());
        JTextField killCountInterval = new JTextField(String.valueOf(view.killCountInterval()));
        JTextField killCountMessage = new JTextField(view.killCountMessage());
        JTextField bestTimeMessage = new JTextField(view.killCountBestTimeMessage());
        JTextArea lootWebhook = textArea(view.lootWebhook(), 2);
        JTextArea pkWebhook = textArea(view.pkWebhook(), 2);
        JCheckBox lootEnabled = new JCheckBox("Enable Loot notifications", view.notifyLoot());
        JCheckBox lootScreenshot = new JCheckBox("Include Loot screenshots", view.lootSendImage());
        JCheckBox lootIcons = new JCheckBox("Include item icons", view.lootIcons());
        JCheckBox playerLoot = new JCheckBox("Include player-kill loot", view.includePlayerLoot());
        JCheckBox redirectPkLoot = new JCheckBox("Route player-kill loot to PK webhook", view.lootRedirectPlayerKill());
        JCheckBox clueLoot = new JCheckBox("Include clue loot", view.lootIncludeClueScrolls());
        JCheckBox gambleLoot = new JCheckBox("Include Barbarian Assault gambles", view.lootIncludeGambles());
        JCheckBox rarityIntersection = new JCheckBox("Require both value and rarity thresholds", view.lootRarityValueIntersection());
        JTextField minimumLootValue = new JTextField(String.valueOf(view.minLootValue()));
        JTextField lootImageValue = new JTextField(String.valueOf(view.lootImageMinValue()));
        JTextField rarityThreshold = new JTextField(String.valueOf(view.lootRarityThreshold()));
        JTextArea itemAllowlist = textArea(view.lootItemAllowlist(), 2);
        JTextArea itemDenylist = textArea(view.lootItemDenylist(), 2);
        JTextArea sourceDenylist = textArea(view.lootSourceDenylist(), 2);
        JTextField lootMessage = new JTextField(view.lootNotifyMessage());
        JTextArea slayerWebhook = textArea(view.slayerWebhook(), 2);
        JCheckBox slayerEnabled = new JCheckBox("Enable Slayer notifications", view.notifySlayer());
        JCheckBox slayerScreenshot = new JCheckBox("Include Slayer screenshots", view.slayerSendImage());
        JTextField slayerThreshold = new JTextField(String.valueOf(view.slayerPointThreshold()));
        JTextField slayerMessage = new JTextField(view.slayerNotifyMessage());
        JTextArea deathWebhook = textArea(view.deathWebhook(), 2);
        JCheckBox deathEnabled = new JCheckBox("Enable Death notifications", view.notifyDeath());
        JCheckBox deathScreenshot = new JCheckBox("Include Death screenshots", view.deathSendImage());
        JCheckBox keptItemEmbeds = new JCheckBox("Include kept-item embeds", view.deathEmbedKeptItems());
        JCheckBox ignoreSafeDeaths = new JCheckBox("Ignore safe deaths", view.deathIgnoreSafe());
        JCheckBox distinguishPvp = new JCheckBox("Use a separate PvP message", view.deathNotifPvpEnabled());
        JTextField deathExceptions = new JTextField(view.deathSafeExceptions().stream()
            .map(Enum::name).collect(Collectors.joining(", ")));
        JTextArea ignoredDeathRegions = textArea(view.deathIgnoredRegions(), 2);
        JTextField minimumLostValue = new JTextField(String.valueOf(view.deathMinValue()));
        JTextField deathMessage = new JTextField(view.deathNotifyMessage());
        JTextField pvpDeathMessage = new JTextField(view.deathNotifPvpMessage());
        JTextArea collectionWebhook = textArea(view.collectionWebhook(), 2);
        JCheckBox collectionEnabled = new JCheckBox("Enable Collection Log notifications", view.notifyCollectionLog());
        JCheckBox collectionScreenshot = new JCheckBox("Include Collection Log screenshots", view.collectionSendImage());
        JTextArea collectionDenylist = textArea(view.collectionDenylist(), 3);
        JTextField collectionMessage = new JTextField(view.collectionNotifyMessage());
        JTextArea diaryWebhook = textArea(view.diaryWebhook(), 2);
        JCheckBox diaryEnabled = new JCheckBox("Enable Achievement Diary notifications", view.notifyAchievementDiary());
        JCheckBox diaryScreenshot = new JCheckBox("Include Achievement Diary screenshots", view.diarySendImage());
        JComboBox<AchievementDiary.Difficulty> diaryDifficulty =
            new JComboBox<>(AchievementDiary.Difficulty.values());
        diaryDifficulty.setSelectedItem(view.minDiaryDifficulty());
        JTextField diaryMessage = new JTextField(view.diaryNotifyMessage());
        JTextArea combatTaskWebhook = textArea(view.combatTaskWebhook(), 2);
        JCheckBox combatTaskEnabled = new JCheckBox("Enable Combat Achievement notifications", view.notifyCombatTask());
        JCheckBox combatTaskScreenshot = new JCheckBox("Include Combat Achievement screenshots", view.combatTaskSendImage());
        JComboBox<CombatAchievementTier> combatTaskTier =
            new JComboBox<>(CombatAchievementTier.values());
        combatTaskTier.setSelectedItem(view.minCombatAchievementTier());
        JTextField combatTaskMessage = new JTextField(view.combatTaskMessage());
        JTextField combatUnlockMessage = new JTextField(view.combatTaskUnlockMessage());
        JTextArea clueWebhook = textArea(view.clueWebhook(), 2);
        JCheckBox clueEnabled = new JCheckBox("Enable Clue Scroll notifications", view.notifyClue());
        JCheckBox clueScreenshot = new JCheckBox("Include Clue Scroll screenshots", view.clueSendImage());
        JCheckBox clueItems = new JCheckBox("Include reward item images", view.clueShowItems());
        JComboBox<ClueTier> clueTier = new JComboBox<>(ClueTier.values());
        clueTier.setSelectedItem(view.clueMinTier());
        JTextField clueMinValue = new JTextField(String.valueOf(view.clueMinValue()));
        JTextField clueImageValue = new JTextField(String.valueOf(view.clueImageMinValue()));
        JTextField clueMessage = new JTextField(view.clueNotifyMessage());
        JCheckBox pkEnabled = new JCheckBox("Enable Player Kill notifications", view.notifyPk());
        JCheckBox pkScreenshot = new JCheckBox("Include Player Kill screenshots", view.pkSendImage());
        JCheckBox pkSkipSafe = new JCheckBox("Skip kills in safe areas", view.pkSkipSafe());
        JCheckBox pkSkipFriendly = new JCheckBox("Skip friends, clanmates, and teammates", view.pkSkipFriendly());
        JCheckBox pkLocation = new JCheckBox("Include kill location", view.pkIncludeLocation());
        JTextField pkMinValue = new JTextField(String.valueOf(view.pkMinValue()));
        JTextField pkMessage = new JTextField(view.pkNotifyMessage());
        JTextArea tradeWebhook = textArea(view.tradeWebhook(), 2);
        JCheckBox tradeEnabled = new JCheckBox("Enable Trade notifications", view.notifyTrades());
        JCheckBox tradeScreenshot = new JCheckBox("Include Trade screenshots", view.tradeSendImage());
        JTextField tradeMinValue = new JTextField(String.valueOf(view.tradeMinValue()));
        JTextField tradeMessage = new JTextField(view.tradeNotifyMessage());
        JTextArea grandExchangeWebhook = textArea(view.grandExchangeWebhook(), 2);
        JCheckBox grandExchangeEnabled = new JCheckBox("Enable Grand Exchange notifications", view.notifyGrandExchange());
        JCheckBox grandExchangeScreenshot = new JCheckBox("Include Grand Exchange screenshots", view.grandExchangeSendImage());
        JCheckBox grandExchangeCancelled = new JCheckBox("Include cancelled offers", view.grandExchangeIncludeCancelled());
        JTextField grandExchangeMinValue = new JTextField(String.valueOf(view.grandExchangeMinValue()));
        JTextField grandExchangeSpacing = new JTextField(String.valueOf(view.grandExchangeProgressSpacingMinutes()));
        JTextField grandExchangeMessage = new JTextField(view.grandExchangeNotifyMessage());
        JTextArea groupStorageWebhook = textArea(view.groupStorageWebhook(), 2);
        JCheckBox groupStorageEnabled = new JCheckBox("Enable Group Storage notifications", view.notifyGroupStorage());
        JCheckBox groupStorageScreenshot = new JCheckBox("Include Group Storage screenshots", view.groupStorageSendImage());
        JCheckBox groupStorageClan = new JCheckBox("Include group name", view.groupStorageIncludeClan());
        JCheckBox groupStoragePrice = new JCheckBox("Include item prices and net value", view.groupStorageIncludePrice());
        JTextField groupStorageMinValue = new JTextField(String.valueOf(view.groupStorageMinValue()));
        JTextField groupStorageMessage = new JTextField(view.groupStorageNotifyMessage());
        JTextArea gambleWebhook = textArea(view.gambleWebhook(), 2);
        JCheckBox gambleEnabled = new JCheckBox("Enable BA Gamble notifications", view.notifyGamble());
        JCheckBox gambleScreenshot = new JCheckBox("Include BA Gamble screenshots", view.gambleSendImage());
        JCheckBox gambleRare = new JCheckBox("Always notify for rare gamble loot", view.gambleRareLoot());
        JTextField gambleInterval = new JTextField(String.valueOf(view.gambleInterval()));
        JTextField gambleMessage = new JTextField(view.gambleNotifyMessage());
        JTextField gambleRareMessage = new JTextField(view.gambleRareNotifyMessage());
        JTextArea leaguesWebhook = textArea(view.leaguesWebhook(), 2);
        JCheckBox leaguesEnabled = new JCheckBox("Enable Leagues notifications", view.notifyLeagues());
        JCheckBox leaguesScreenshot = new JCheckBox("Include Leagues screenshots", view.leaguesSendImage());
        JCheckBox leaguesAreas = new JCheckBox("Notify for area unlocks", view.leaguesAreaUnlock());
        JCheckBox leaguesRelics = new JCheckBox("Notify for relic unlocks", view.leaguesRelicUnlock());
        JCheckBox leaguesTasks = new JCheckBox("Notify for completed tasks", view.leaguesTaskCompletion());
        JCheckBox leaguesMasteries = new JCheckBox("Notify for combat mastery unlocks", view.leaguesMasteryUnlock());
        JComboBox<LeagueTaskDifficulty> leaguesTaskTier = new JComboBox<>(LeagueTaskDifficulty.values());
        leaguesTaskTier.setSelectedItem(view.leaguesTaskMinTier());
        JTextArea speedrunWebhook = textArea(view.speedrunWebhook(), 2);
        JCheckBox speedrunEnabled = new JCheckBox("Enable Speedrun notifications", view.notifySpeedrun());
        JCheckBox speedrunScreenshot = new JCheckBox("Include Speedrun screenshots", view.speedrunSendImage());
        JCheckBox speedrunPbOnly = new JCheckBox("Notify only for personal bests", view.speedrunPBOnly());
        JTextField speedrunPbMessage = new JTextField(view.speedrunPBMessage());
        JTextField speedrunMessage = new JTextField(view.speedrunMessage());
        JTextArea chatWebhook = textArea(view.chatWebhook(), 2);
        JCheckBox chatEnabled = new JCheckBox("Enable custom Chat notifications", view.notifyChat());
        JCheckBox chatScreenshot = new JCheckBox("Include Chat screenshots", view.chatSendImage());
        JTextArea chatTypes = textArea(view.chatMessageTypes().stream().map(Enum::name)
            .collect(Collectors.joining(", ")), 2);
        JTextArea chatPatterns = textArea(view.chatPatterns(), 5);
        JTextField chatMessage = new JTextField(view.chatNotifyMessage());
        JTextArea metadataWebhook = textArea(view.metadataWebhook(), 2);
        JTextArea externalWebhook = textArea(view.externalWebhook(), 2);
        JCheckBox externalEnabled = new JCheckBox("Enable External Plugin notifications", view.notifyExternal());
        JComboBox<ExternalScreenshotPolicy> externalScreenshot = new JComboBox<>(ExternalScreenshotPolicy.values());
        externalScreenshot.setSelectedItem(view.externalSendImage());

        ProfileEditor form = new ProfileEditor();
        form.addPinned(new JLabel("Profile name"));
        form.addPinned(profileName);
        form.addPinned(new JLabel("Primary webhook URLs (one per line)"));
        form.addPinned(new JScrollPane(primaryWebhooks));
        form.section("Pets");
        form.add(new JLabel("Pet webhook override (optional)"));
        form.add(new JScrollPane(petWebhook));
        form.add(petEnabled);
        form.add(petScreenshot);
        form.add(petDuplicates);
        form.add(new JLabel("Pet message template"));
        form.add(petMessage);
        form.section("Quests");
        form.add(new JLabel("Quest webhook override (optional)"));
        form.add(new JScrollPane(questWebhook));
        form.add(questEnabled);
        form.add(questScreenshot);
        form.add(new JLabel("Quest message template"));
        form.add(questMessage);
        form.section("Levels and XP");
        form.add(new JLabel("Level webhook override (optional)"));
        form.add(new JScrollPane(levelWebhook));
        form.add(levelEnabled);
        form.add(levelScreenshot);
        form.add(virtualLevels);
        form.add(combatLevels);
        form.add(new JLabel("Level interval"));
        form.add(levelInterval);
        form.add(new JLabel("Minimum level"));
        form.add(minimumLevel);
        form.add(new JLabel("Minimum screenshot level"));
        form.add(screenshotLevel);
        form.add(new JLabel("Interval override level (0 disables)"));
        form.add(intervalOverride);
        form.add(new JLabel("Post-99 XP interval in millions (0 disables)"));
        form.add(xpInterval);
        form.add(new JLabel("Level and XP message template"));
        form.add(levelMessage);
        form.section("Kill Count");
        form.add(new JLabel("Kill Count webhook override (optional)"));
        form.add(new JScrollPane(killCountWebhook));
        form.add(killCountEnabled);
        form.add(killCountScreenshot);
        form.add(initialKill);
        form.add(personalBest);
        form.add(penanceQueen);
        form.add(new JLabel("Kill Count interval"));
        form.add(killCountInterval);
        form.add(new JLabel("Kill Count message template"));
        form.add(killCountMessage);
        form.add(new JLabel("Personal-best message template"));
        form.add(bestTimeMessage);
        form.section("Loot");
        form.add(new JLabel("Loot webhook override (optional)"));
        form.add(new JScrollPane(lootWebhook));
        form.add(new JLabel("PK webhook override (optional)"));
        form.add(new JScrollPane(pkWebhook));
        form.add(lootEnabled);
        form.add(lootScreenshot);
        form.add(lootIcons);
        form.add(playerLoot);
        form.add(redirectPkLoot);
        form.add(clueLoot);
        form.add(gambleLoot);
        form.add(new JLabel("Minimum loot value"));
        form.add(minimumLootValue);
        form.add(new JLabel("Minimum screenshot value"));
        form.add(lootImageValue);
        form.add(new JLabel("Rarity denominator (0 disables)"));
        form.add(rarityThreshold);
        form.add(rarityIntersection);
        form.add(new JLabel("Item allowlist (one expression per line)"));
        form.add(new JScrollPane(itemAllowlist));
        form.add(new JLabel("Item denylist (one expression per line)"));
        form.add(new JScrollPane(itemDenylist));
        form.add(new JLabel("Source denylist (one source per line)"));
        form.add(new JScrollPane(sourceDenylist));
        form.add(new JLabel("Loot message template"));
        form.add(lootMessage);
        form.section("Slayer");
        form.add(new JLabel("Slayer webhook override (optional)"));
        form.add(new JScrollPane(slayerWebhook));
        form.add(slayerEnabled);
        form.add(slayerScreenshot);
        form.add(new JLabel("Minimum Slayer points"));
        form.add(slayerThreshold);
        form.add(new JLabel("Slayer message template"));
        form.add(slayerMessage);
        form.section("Deaths");
        form.add(new JLabel("Death webhook override (optional)"));
        form.add(new JScrollPane(deathWebhook));
        form.add(deathEnabled);
        form.add(deathScreenshot);
        form.add(keptItemEmbeds);
        form.add(ignoreSafeDeaths);
        form.add(distinguishPvp);
        form.add(new JLabel("Safe-death exception IDs (comma-separated)"));
        form.add(deathExceptions);
        form.add(new JLabel("Ignored region IDs"));
        form.add(new JScrollPane(ignoredDeathRegions));
        form.add(new JLabel("Minimum lost value"));
        form.add(minimumLostValue);
        form.add(new JLabel("Death message template"));
        form.add(deathMessage);
        form.add(new JLabel("PvP death message template"));
        form.add(pvpDeathMessage);
        form.section("Collection Log");
        form.add(new JLabel("Collection Log webhook override (optional)"));
        form.add(new JScrollPane(collectionWebhook));
        form.add(collectionEnabled);
        form.add(collectionScreenshot);
        form.add(new JLabel("Collection item denylist (one expression per line)"));
        form.add(new JScrollPane(collectionDenylist));
        form.add(new JLabel("Collection message template"));
        form.add(collectionMessage);
        form.section("Achievement Diaries");
        form.add(new JLabel("Achievement Diary webhook override (optional)"));
        form.add(new JScrollPane(diaryWebhook));
        form.add(diaryEnabled);
        form.add(diaryScreenshot);
        form.add(new JLabel("Minimum diary difficulty"));
        form.add(diaryDifficulty);
        form.add(new JLabel("Achievement Diary message template"));
        form.add(diaryMessage);
        form.section("Combat Achievements");
        form.add(new JLabel("Combat Achievement webhook override (optional)"));
        form.add(new JScrollPane(combatTaskWebhook));
        form.add(combatTaskEnabled);
        form.add(combatTaskScreenshot);
        form.add(new JLabel("Minimum combat-task tier"));
        form.add(combatTaskTier);
        form.add(new JLabel("Combat-task message template"));
        form.add(combatTaskMessage);
        form.add(new JLabel("Tier-unlock message template"));
        form.add(combatUnlockMessage);
        form.section("Clue Scrolls");
        form.add(new JLabel("Clue Scroll webhook override (optional)"));
        form.add(new JScrollPane(clueWebhook));
        form.add(clueEnabled);
        form.add(clueScreenshot);
        form.add(clueItems);
        form.add(new JLabel("Minimum clue tier"));
        form.add(clueTier);
        form.add(new JLabel("Minimum reward value"));
        form.add(clueMinValue);
        form.add(new JLabel("Minimum screenshot value"));
        form.add(clueImageValue);
        form.add(new JLabel("Clue message template"));
        form.add(clueMessage);
        form.section("Player Kills");
        form.add(new JLabel("Player Kill settings (uses the PK webhook above)"));
        form.add(pkEnabled);
        form.add(pkScreenshot);
        form.add(pkSkipSafe);
        form.add(pkSkipFriendly);
        form.add(pkLocation);
        form.add(new JLabel("Minimum opponent equipment value"));
        form.add(pkMinValue);
        form.add(new JLabel("Player Kill message template"));
        form.add(pkMessage);
        form.section("Player Trades");
        form.add(new JLabel("Trade webhook override (optional)"));
        form.add(new JScrollPane(tradeWebhook));
        form.add(tradeEnabled);
        form.add(tradeScreenshot);
        form.add(new JLabel("Minimum combined trade value"));
        form.add(tradeMinValue);
        form.add(new JLabel("Trade message template"));
        form.add(tradeMessage);
        form.section("Grand Exchange");
        form.add(new JLabel("Grand Exchange webhook override (optional)"));
        form.add(new JScrollPane(grandExchangeWebhook));
        form.add(grandExchangeEnabled);
        form.add(grandExchangeScreenshot);
        form.add(grandExchangeCancelled);
        form.add(new JLabel("Minimum transacted value"));
        form.add(grandExchangeMinValue);
        form.add(new JLabel("In-progress spacing in minutes (-1 disables)"));
        form.add(grandExchangeSpacing);
        form.add(new JLabel("Grand Exchange message template"));
        form.add(grandExchangeMessage);
        form.section("Group Storage");
        form.add(new JLabel("Group Storage webhook override (optional)"));
        form.add(new JScrollPane(groupStorageWebhook));
        form.add(groupStorageEnabled);
        form.add(groupStorageScreenshot);
        form.add(groupStorageClan);
        form.add(groupStoragePrice);
        form.add(new JLabel("Minimum deposit or withdrawal value"));
        form.add(groupStorageMinValue);
        form.add(new JLabel("Group Storage message template"));
        form.add(groupStorageMessage);
        form.section("Barbarian Assault Gambles");
        form.add(new JLabel("BA Gamble webhook override (optional)"));
        form.add(new JScrollPane(gambleWebhook));
        form.add(gambleEnabled);
        form.add(gambleScreenshot);
        form.add(gambleRare);
        form.add(new JLabel("Gamble notification interval"));
        form.add(gambleInterval);
        form.add(new JLabel("Gamble message template"));
        form.add(gambleMessage);
        form.add(new JLabel("Rare gamble loot message template"));
        form.add(gambleRareMessage);
        form.section("Leagues");
        form.add(new JLabel("Leagues webhook override (optional)"));
        form.add(new JScrollPane(leaguesWebhook));
        form.add(leaguesEnabled);
        form.add(leaguesScreenshot);
        form.add(leaguesAreas);
        form.add(leaguesRelics);
        form.add(leaguesTasks);
        form.add(leaguesMasteries);
        form.add(new JLabel("Minimum Leagues task tier"));
        form.add(leaguesTaskTier);
        form.section("Speedruns");
        form.add(new JLabel("Speedrun webhook override (optional)"));
        form.add(new JScrollPane(speedrunWebhook));
        form.add(speedrunEnabled);
        form.add(speedrunScreenshot);
        form.add(speedrunPbOnly);
        form.add(new JLabel("Speedrun personal-best message template"));
        form.add(speedrunPbMessage);
        form.add(new JLabel("Speedrun completion message template"));
        form.add(speedrunMessage);
        form.section("Custom Chat Messages");
        form.add(new JLabel("Chat webhook override (optional)"));
        form.add(new JScrollPane(chatWebhook));
        form.add(chatEnabled);
        form.add(chatScreenshot);
        form.add(new JLabel("Chat message types (comma-separated enum names)"));
        form.add(new JScrollPane(chatTypes));
        form.add(new JLabel("Chat patterns (one per line)"));
        form.add(new JScrollPane(chatPatterns));
        form.add(new JLabel("Chat message template"));
        form.add(chatMessage);
        form.section("Integrations");
        form.add(new JLabel("Metadata handler URLs (optional; enables metadata events)"));
        form.add(new JScrollPane(metadataWebhook));
        form.add(new JLabel("External Plugin webhook override (optional)"));
        form.add(new JScrollPane(externalWebhook));
        form.add(externalEnabled);
        form.add(new JLabel("External Plugin screenshot policy"));
        form.add(externalScreenshot);

        form.setPreferredSize(new Dimension(600, 600));

        int choice = JOptionPane.showConfirmDialog(
            this,
            form,
            "Configure " + profile.getName(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        runAction(() -> {
            Map<String, JsonElement> settings = new LinkedHashMap<>(profile.getSettings());
            settings.put("petEnabled", new JsonPrimitive(petEnabled.isSelected()));
            settings.put("petSendImage", new JsonPrimitive(petScreenshot.isSelected()));
            settings.put("petIncludeDuplicates", new JsonPrimitive(petDuplicates.isSelected()));
            settings.put("petNotifMessage", new JsonPrimitive(petMessage.getText()));
            settings.put("questEnabled", new JsonPrimitive(questEnabled.isSelected()));
            settings.put("questSendImage", new JsonPrimitive(questScreenshot.isSelected()));
            settings.put("questNotifMessage", new JsonPrimitive(questMessage.getText()));
            settings.put("levelEnabled", new JsonPrimitive(levelEnabled.isSelected()));
            settings.put("levelSendImage", new JsonPrimitive(levelScreenshot.isSelected()));
            settings.put("levelNotifyVirtual", new JsonPrimitive(virtualLevels.isSelected()));
            settings.put("levelNotifyCombat", new JsonPrimitive(combatLevels.isSelected()));
            settings.put("levelInterval", new JsonPrimitive(parseNonNegative(levelInterval, "Level interval")));
            settings.put("levelMinValue", new JsonPrimitive(parseNonNegative(minimumLevel, "Minimum level")));
            settings.put("levelMinScreenshotValue", new JsonPrimitive(parseNonNegative(screenshotLevel, "Minimum screenshot level")));
            settings.put("levelIntervalOverride", new JsonPrimitive(parseNonNegative(intervalOverride, "Interval override")));
            settings.put("xpInterval", new JsonPrimitive(parseNonNegative(xpInterval, "XP interval")));
            settings.put("levelNotifMessage", new JsonPrimitive(levelMessage.getText()));
            settings.put("killCountEnabled", new JsonPrimitive(killCountEnabled.isSelected()));
            settings.put("killCountSendImage", new JsonPrimitive(killCountScreenshot.isSelected()));
            settings.put("killCountInitial", new JsonPrimitive(initialKill.isSelected()));
            settings.put("killCountPB", new JsonPrimitive(personalBest.isSelected()));
            settings.put("killCountPenanceQueen", new JsonPrimitive(penanceQueen.isSelected()));
            settings.put("killCountInterval", new JsonPrimitive(parseNonNegative(killCountInterval, "Kill Count interval")));
            settings.put("killCountMessage", new JsonPrimitive(killCountMessage.getText()));
            settings.put("killCountBestTimeMessage", new JsonPrimitive(bestTimeMessage.getText()));
            settings.put("lootEnabled", new JsonPrimitive(lootEnabled.isSelected()));
            settings.put("lootSendImage", new JsonPrimitive(lootScreenshot.isSelected()));
            settings.put("lootIcons", new JsonPrimitive(lootIcons.isSelected()));
            settings.put("lootIncludePlayer", new JsonPrimitive(playerLoot.isSelected()));
            settings.put("lootRedirectPlayerKill", new JsonPrimitive(redirectPkLoot.isSelected()));
            settings.put("lootIncludeClueScrolls", new JsonPrimitive(clueLoot.isSelected()));
            settings.put("lootIncludeGambles", new JsonPrimitive(gambleLoot.isSelected()));
            settings.put("minLootValue", new JsonPrimitive(parseNonNegative(minimumLootValue, "Minimum loot value")));
            settings.put("lootImageMinValue", new JsonPrimitive(parseNonNegative(lootImageValue, "Minimum loot screenshot value")));
            settings.put("lootRarityThreshold", new JsonPrimitive(parseNonNegative(rarityThreshold, "Loot rarity denominator")));
            settings.put("lootRarityValueIntersection", new JsonPrimitive(rarityIntersection.isSelected()));
            settings.put("lootItemAllowlist", new JsonPrimitive(itemAllowlist.getText()));
            settings.put("lootItemDenylist", new JsonPrimitive(itemDenylist.getText()));
            settings.put("lootSourceDenylist", new JsonPrimitive(sourceDenylist.getText()));
            settings.put("lootNotifMessage", new JsonPrimitive(lootMessage.getText()));
            settings.put("slayerEnabled", new JsonPrimitive(slayerEnabled.isSelected()));
            settings.put("slayerSendImage", new JsonPrimitive(slayerScreenshot.isSelected()));
            settings.put("slayerPointThreshold", new JsonPrimitive(parseNonNegative(slayerThreshold, "Minimum Slayer points")));
            settings.put("slayerNotifMessage", new JsonPrimitive(slayerMessage.getText()));
            settings.put("deathEnabled", new JsonPrimitive(deathEnabled.isSelected()));
            settings.put("deathSendImage", new JsonPrimitive(deathScreenshot.isSelected()));
            settings.put("deathEmbedProtected", new JsonPrimitive(keptItemEmbeds.isSelected()));
            settings.put("deathIgnoreSafe", new JsonPrimitive(ignoreSafeDeaths.isSelected()));
            settings.put("deathNotifPvpEnabled", new JsonPrimitive(distinguishPvp.isSelected()));
            settings.put("deathSafeExceptions", gson.toJsonTree(parseDeathExceptions(deathExceptions.getText())));
            settings.put("deathIgnoredRegions", new JsonPrimitive(ignoredDeathRegions.getText()));
            settings.put("deathMinValue", new JsonPrimitive(parseNonNegative(minimumLostValue, "Minimum lost value")));
            settings.put("deathNotifMessage", new JsonPrimitive(deathMessage.getText()));
            settings.put("deathNotifPvpMessage", new JsonPrimitive(pvpDeathMessage.getText()));
            settings.put("collectionLogEnabled", new JsonPrimitive(collectionEnabled.isSelected()));
            settings.put("collectionSendImage", new JsonPrimitive(collectionScreenshot.isSelected()));
            settings.put("collectionDenylist", new JsonPrimitive(collectionDenylist.getText()));
            settings.put("collectionNotifMessage", new JsonPrimitive(collectionMessage.getText()));
            settings.put("diaryEnabled", new JsonPrimitive(diaryEnabled.isSelected()));
            settings.put("diarySendImage", new JsonPrimitive(diaryScreenshot.isSelected()));
            settings.put("diaryMinDifficulty", new JsonPrimitive(
                ((AchievementDiary.Difficulty) diaryDifficulty.getSelectedItem()).name()));
            settings.put("diaryMessage", new JsonPrimitive(diaryMessage.getText()));
            settings.put("combatTaskEnabled", new JsonPrimitive(combatTaskEnabled.isSelected()));
            settings.put("combatTaskSendImage", new JsonPrimitive(combatTaskScreenshot.isSelected()));
            settings.put("combatTaskMinTier", new JsonPrimitive(
                ((CombatAchievementTier) combatTaskTier.getSelectedItem()).name()));
            settings.put("combatTaskMessage", new JsonPrimitive(combatTaskMessage.getText()));
            settings.put("combatTaskUnlockMessage", new JsonPrimitive(combatUnlockMessage.getText()));
            settings.put("clueEnabled", new JsonPrimitive(clueEnabled.isSelected()));
            settings.put("clueSendImage", new JsonPrimitive(clueScreenshot.isSelected()));
            settings.put("clueShowItems", new JsonPrimitive(clueItems.isSelected()));
            settings.put("clueMinTier", new JsonPrimitive(((ClueTier) clueTier.getSelectedItem()).name()));
            settings.put("clueMinValue", new JsonPrimitive(parseNonNegative(clueMinValue, "Minimum clue reward value")));
            settings.put("clueImageMinValue", new JsonPrimitive(parseNonNegative(clueImageValue, "Minimum clue screenshot value")));
            settings.put("clueNotifMessage", new JsonPrimitive(clueMessage.getText()));
            settings.put("pkEnabled", new JsonPrimitive(pkEnabled.isSelected()));
            settings.put("pkSendImage", new JsonPrimitive(pkScreenshot.isSelected()));
            settings.put("pkSkipSafe", new JsonPrimitive(pkSkipSafe.isSelected()));
            settings.put("pkSkipFriendly", new JsonPrimitive(pkSkipFriendly.isSelected()));
            settings.put("pkIncludeLocation", new JsonPrimitive(pkLocation.isSelected()));
            settings.put("pkMinValue", new JsonPrimitive(parseNonNegative(pkMinValue, "Minimum opponent equipment value")));
            settings.put("pkNotifyMessage", new JsonPrimitive(pkMessage.getText()));
            settings.put("notifyTrades", new JsonPrimitive(tradeEnabled.isSelected()));
            settings.put("tradeSendImage", new JsonPrimitive(tradeScreenshot.isSelected()));
            settings.put("tradeMinValue", new JsonPrimitive(parseNonNegative(tradeMinValue, "Minimum combined trade value")));
            settings.put("tradeNotifyMessage", new JsonPrimitive(tradeMessage.getText()));
            settings.put("notifyGrandExchange", new JsonPrimitive(grandExchangeEnabled.isSelected()));
            settings.put("grandExchangeSendImage", new JsonPrimitive(grandExchangeScreenshot.isSelected()));
            settings.put("grandExchangeIncludeCancelled", new JsonPrimitive(grandExchangeCancelled.isSelected()));
            settings.put("grandExchangeMinValue", new JsonPrimitive(parseNonNegative(grandExchangeMinValue, "Minimum Grand Exchange value")));
            settings.put("grandExchangeProgressSpacingMinutes", new JsonPrimitive(
                parseMinimum(grandExchangeSpacing, "Grand Exchange progress spacing", -1)));
            settings.put("grandExchangeNotifyMessage", new JsonPrimitive(grandExchangeMessage.getText()));
            settings.put("groupStorageEnabled", new JsonPrimitive(groupStorageEnabled.isSelected()));
            settings.put("groupStorageSendImage", new JsonPrimitive(groupStorageScreenshot.isSelected()));
            settings.put("groupStorageIncludeClan", new JsonPrimitive(groupStorageClan.isSelected()));
            settings.put("groupStorageIncludePrice", new JsonPrimitive(groupStoragePrice.isSelected()));
            settings.put("groupStorageMinValue", new JsonPrimitive(parseNonNegative(groupStorageMinValue, "Minimum Group Storage value")));
            settings.put("groupStorageNotifyMessage", new JsonPrimitive(groupStorageMessage.getText()));
            settings.put("gambleEnabled", new JsonPrimitive(gambleEnabled.isSelected()));
            settings.put("gambleSendImage", new JsonPrimitive(gambleScreenshot.isSelected()));
            settings.put("gambleRareLoot", new JsonPrimitive(gambleRare.isSelected()));
            settings.put("gambleInterval", new JsonPrimitive(parseMinimum(gambleInterval, "Gamble interval", 1)));
            settings.put("gambleNotifMessage", new JsonPrimitive(gambleMessage.getText()));
            settings.put("gambleRareNotifMessage", new JsonPrimitive(gambleRareMessage.getText()));
            settings.put("notifyLeagues", new JsonPrimitive(leaguesEnabled.isSelected()));
            settings.put("leaguesSendImage", new JsonPrimitive(leaguesScreenshot.isSelected()));
            settings.put("leaguesAreaUnlock", new JsonPrimitive(leaguesAreas.isSelected()));
            settings.put("leaguesRelicUnlock", new JsonPrimitive(leaguesRelics.isSelected()));
            settings.put("leaguesTaskCompletion", new JsonPrimitive(leaguesTasks.isSelected()));
            settings.put("leaguesMasteryUnlock", new JsonPrimitive(leaguesMasteries.isSelected()));
            settings.put("leaguesTaskMinTier", new JsonPrimitive(
                ((LeagueTaskDifficulty) leaguesTaskTier.getSelectedItem()).name()));
            settings.put("speedrunEnabled", new JsonPrimitive(speedrunEnabled.isSelected()));
            settings.put("speedrunSendImage", new JsonPrimitive(speedrunScreenshot.isSelected()));
            settings.put("speedrunPBOnly", new JsonPrimitive(speedrunPbOnly.isSelected()));
            settings.put("speedrunPBMessage", new JsonPrimitive(speedrunPbMessage.getText()));
            settings.put("speedrunMessage", new JsonPrimitive(speedrunMessage.getText()));
            settings.put("notifyChat", new JsonPrimitive(chatEnabled.isSelected()));
            settings.put("chatSendImage", new JsonPrimitive(chatScreenshot.isSelected()));
            settings.put("chatMessageTypes", gson.toJsonTree(parseChatTypes(chatTypes.getText())));
            settings.put("chatPatterns", new JsonPrimitive(chatPatterns.getText()));
            settings.put("chatNotifyMessage", new JsonPrimitive(chatMessage.getText()));
            settings.put("notifyExternal", new JsonPrimitive(externalEnabled.isSelected()));
            settings.put("externalSendImage", new JsonPrimitive(
                ((ExternalScreenshotPolicy) externalScreenshot.getSelectedItem()).name()));

            Map<String, List<String>> overrides = new LinkedHashMap<>(profile.getWebhooks().getOverrides());
            putOverride(overrides, "petWebhook", parseUrls(petWebhook.getText()));
            List<String> questUrls = parseUrls(questWebhook.getText());
            if (questUrls.isEmpty()) {
                overrides.remove("questWebhook");
            } else {
                overrides.put("questWebhook", questUrls);
            }
            List<String> levelUrls = parseUrls(levelWebhook.getText());
            if (levelUrls.isEmpty()) {
                overrides.remove("levelWebhook");
            } else {
                overrides.put("levelWebhook", levelUrls);
            }
            List<String> killCountUrls = parseUrls(killCountWebhook.getText());
            if (killCountUrls.isEmpty()) {
                overrides.remove("killCountWebhook");
            } else {
                overrides.put("killCountWebhook", killCountUrls);
            }
            putOverride(overrides, "lootWebhook", parseUrls(lootWebhook.getText()));
            putOverride(overrides, "pkWebhook", parseUrls(pkWebhook.getText()));
            putOverride(overrides, "slayerWebhook", parseUrls(slayerWebhook.getText()));
            putOverride(overrides, "deathWebhook", parseUrls(deathWebhook.getText()));
            putOverride(overrides, "collectionWebhook", parseUrls(collectionWebhook.getText()));
            putOverride(overrides, "diaryWebhook", parseUrls(diaryWebhook.getText()));
            putOverride(overrides, "combatTaskWebhook", parseUrls(combatTaskWebhook.getText()));
            putOverride(overrides, "clueWebhook", parseUrls(clueWebhook.getText()));
            putOverride(overrides, "tradeWebhook", parseUrls(tradeWebhook.getText()));
            putOverride(overrides, "grandExchangeWebhook", parseUrls(grandExchangeWebhook.getText()));
            putOverride(overrides, "groupStorageWebhook", parseUrls(groupStorageWebhook.getText()));
            putOverride(overrides, "gambleWebhook", parseUrls(gambleWebhook.getText()));
            putOverride(overrides, "leaguesWebhook", parseUrls(leaguesWebhook.getText()));
            putOverride(overrides, "speedrunWebhook", parseUrls(speedrunWebhook.getText()));
            putOverride(overrides, "chatWebhook", parseUrls(chatWebhook.getText()));
            putOverride(overrides, "metadataWebhook", parseUrls(metadataWebhook.getText()));
            putOverride(overrides, "externalWebhook", parseUrls(externalWebhook.getText()));

            profileService.update(profile.toBuilder()
                .name(profileName.getText().trim())
                .settings(settings)
                .webhooks(new ProfileWebhooks(parseUrls(primaryWebhooks.getText()), overrides))
                .build());
        });
    }

    private void deleteProfile(PigeonProfile profile) {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Delete profile \"" + profile.getName() + "\"? This cannot be undone.",
            "Delete profile",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice == JOptionPane.OK_OPTION) {
            runAction(() -> profileService.delete(profile.getId()));
        }
    }

    private void exportProfile(PigeonProfile profile) {
        Object[] options = { "Safe export", "Include webhooks", "Cancel" };
        int choice = JOptionPane.showOptionDialog(
            this,
            "Safe export removes webhook credentials. Including webhooks makes the export secret and should only be shared with people you trust.",
            "Export " + profile.getName(),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        );
        if (choice != 0 && choice != 1) {
            return;
        }

        boolean includeSecrets = choice == 1;
        try {
            String json = profileService.exportProfile(profile.getId(), includeSecrets);
            Utils.copyToClipboard(json)
                .thenRun(() -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    includeSecrets ? "Secret profile export copied to clipboard." : "Safe profile export copied to clipboard.",
                    "Profile exported",
                    JOptionPane.INFORMATION_MESSAGE
                )))
                .exceptionally(error -> {
                    SwingUtilities.invokeLater(() -> showError("Could not copy the profile to the clipboard", error));
                    return null;
                });
        } catch (RuntimeException exception) {
            showError("Could not export the profile", exception);
        }
    }

    private void importProfile() {
        Utils.readClipboard()
            .thenApply(profileService::previewImport)
            .thenAccept(profile -> SwingUtilities.invokeLater(() -> confirmImport(profile)))
            .exceptionally(error -> {
                SwingUtilities.invokeLater(() -> showError("Clipboard does not contain a valid Pigeon profile", error));
                return null;
            });
    }

    private void confirmImport(PigeonProfile profile) {
        int webhookCount = countWebhooks(profile.getWebhooks());
        String credentialWarning = webhookCount == 0
            ? "This export contains no webhook credentials."
            : "This export contains " + webhookCount + " webhook URL(s). Treat them as credentials.";
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Import profile \"" + profile.getName() + "\"?\n" + credentialWarning +
                "\nThe imported profile will start disabled.",
            "Import profile",
            JOptionPane.OK_CANCEL_OPTION,
            webhookCount == 0 ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE
        );
        if (choice == JOptionPane.OK_OPTION) {
            runAction(() -> profileService.importAsNew(profile));
        }
    }

    private int countWebhooks(ProfileWebhooks webhooks) {
        int count = webhooks.getPrimary().size();
        for (List<String> urls : webhooks.getOverrides().values()) {
            count += urls.size();
        }
        return count;
    }

    private JTextArea textArea(String value, int rows) {
        JTextArea area = new JTextArea(value, rows, 28);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private List<String> parseUrls(String value) {
        return Arrays.stream(value.split("\\R"))
            .map(String::trim)
            .filter(url -> !url.isEmpty())
            .collect(Collectors.toList());
    }

    private int parseNonNegative(JTextField field, String label) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ProfileValidationException(label + " must be a non-negative whole number");
        }
    }

    private int parseMinimum(JTextField field, String label, int minimum) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < minimum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ProfileValidationException(label + " must be a whole number of at least " + minimum);
        }
    }

    private void putOverride(Map<String, List<String>> overrides, String key, List<String> urls) {
        if (urls.isEmpty()) {
            overrides.remove(key);
        } else {
            overrides.put(key, urls);
        }
    }

    private Set<ExceptionalDeath> parseDeathExceptions(String value) {
        Set<ExceptionalDeath> result = EnumSet.noneOf(ExceptionalDeath.class);
        for (String token : value.split("[,\\s]+")) {
            if (token.isBlank()) continue;
            try {
                result.add(ExceptionalDeath.valueOf(token.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new ProfileValidationException("Unknown safe-death exception: " + token);
            }
        }
        return result;
    }

    private Set<ChatNotificationType> parseChatTypes(String value) {
        Set<ChatNotificationType> result = EnumSet.noneOf(ChatNotificationType.class);
        for (String token : value.split("[,\\s]+")) {
            if (token.isBlank()) continue;
            try {
                result.add(ChatNotificationType.valueOf(token.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new ProfileValidationException("Unknown chat message type: " + token);
            }
        }
        return result;
    }

    private static final class ProfileEditor extends JPanel {
        private final JPanel pinned = new JPanel();
        private final JPanel sections = new JPanel();
        private SectionPanel currentSection;

        private ProfileEditor() {
            super(new BorderLayout());
            pinned.setLayout(new BoxLayout(pinned, BoxLayout.Y_AXIS));
            pinned.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
            ));
            super.add(pinned, BorderLayout.NORTH);

            sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
            sections.setBorder(BorderFactory.createEmptyBorder(8, 8, 12, 8));

            JScrollPane scroll = new JScrollPane(sections);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            super.add(scroll, BorderLayout.CENTER);
        }

        private void addPinned(Component component) {
            addField(pinned, component);
        }

        private void section(String heading) {
            currentSection = new SectionPanel(heading, false);
            currentSection.setAlignmentX(Component.LEFT_ALIGNMENT);
            sections.add(currentSection);
            sections.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        @Override
        public Component add(Component component) {
            if (currentSection == null) {
                return super.add(component);
            }

            currentSection.addField(component);
            if (component instanceof JCheckBox) {
                JCheckBox checkBox = (JCheckBox) component;
                if (checkBox.isSelected() && checkBox.getText().startsWith("Enable ")) {
                    currentSection.setExpanded(true);
                }
            }
            return component;
        }

        private static void addField(JPanel panel, Component component) {
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            Dimension preferred = component.getPreferredSize();
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
            panel.add(component);
            panel.add(Box.createRigidArea(new Dimension(0, 4)));
        }
    }

    private static final class SectionPanel extends JPanel {
        private final String heading;
        private final JButton toggle = new JButton();
        private final JPanel body = new JPanel();
        private boolean expanded;

        private SectionPanel(String heading, boolean expanded) {
            super(new BorderLayout());
            this.heading = heading;
            setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));

            toggle.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            toggle.setForeground(ColorScheme.BRAND_ORANGE);
            toggle.setFont(toggle.getFont().deriveFont(Font.BOLD, 14f));
            toggle.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
            toggle.setFocusPainted(false);
            toggle.addActionListener(event -> setExpanded(!this.expanded));
            add(toggle, BorderLayout.NORTH);

            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(BorderFactory.createEmptyBorder(7, 10, 9, 10));
            add(body, BorderLayout.CENTER);
            setExpanded(expanded);
        }

        private void addField(Component component) {
            ProfileEditor.addField(body, component);
        }

        private void setExpanded(boolean expanded) {
            this.expanded = expanded;
            body.setVisible(expanded);
            toggle.setText((expanded ? "\u25BE  " : "\u25B8  ") + heading);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }

    private String promptForName(String title, String initialValue) {
        JTextField nameField = new JTextField(initialValue, 28);
        JPanel prompt = new JPanel();
        prompt.setLayout(new BoxLayout(prompt, BoxLayout.Y_AXIS));
        JLabel instruction = new JLabel(
            "Enter the name of this profile (max " + ProfileCodec.MAX_NAME_LENGTH + " chars)."
        );
        instruction.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        prompt.add(instruction);
        prompt.add(Box.createRigidArea(new Dimension(0, 7)));
        prompt.add(nameField);

        while (true) {
            SwingUtilities.invokeLater(() -> {
                nameField.requestFocusInWindow();
                nameField.selectAll();
            });
            int choice = JOptionPane.showConfirmDialog(
                this,
                prompt,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Profile name is required.", title,
                    JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (name.length() > ProfileCodec.MAX_NAME_LENGTH) {
                JOptionPane.showMessageDialog(this,
                    "Profile name cannot exceed " + ProfileCodec.MAX_NAME_LENGTH + " characters.",
                    title,
                    JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return name;
        }
    }

    private void runAction(Runnable action) {
        try {
            action.run();
            refresh();
        } catch (RuntimeException exception) {
            showError("Profile operation failed", exception);
            refresh();
        }
    }

    private void showError(String message, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String detail = cause instanceof ProfileValidationException || cause.getMessage() != null
            ? cause.getMessage()
            : cause.getClass().getSimpleName();
        JOptionPane.showMessageDialog(
            this,
            message + (detail == null ? "" : ":\n" + detail),
            "Pigeon",
            JOptionPane.ERROR_MESSAGE
        );
    }
}
