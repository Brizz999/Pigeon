package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.GroupStorageNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ItemUtils;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Tracks when items are deposited or withdrawn to GIM shared storage.
 * <p>
 * This is achieved by comparing snapshots of the player's inventory
 * when the storage is opened and when the transaction is saved.
 * When the difference between these two snapshots is non-empty,
 * we fire a notification (given the configured min value is satisfied).
 */
@Singleton
public class GroupStorageNotifier extends BaseNotifier {

    /**
     * The message to indicate that a list of deposits or withdrawals is empty.
     */
    static final @VisibleForTesting String EMPTY_TRANSACTION = "N/A";

    /**
     * Adds two integers, but yields null if the sum is zero
     * (which removes the entry via Map#merge in {@link GroupStorageNotifier#computeDifference(Map, Map)}).
     */
    private static final BinaryOperator<Integer> SUM;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    /**
     * Items in the player's inventory when the group storage was opened.
     * Entries map item id to total quantity (across stacks).
     */
    private Map<Integer, Integer> initialInventory = Collections.emptyMap();

    @Override
    public boolean isEnabled() {
        return config.notifyGroupStorage() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.groupStorageWebhook();
    }

    public void reset() {
        clientThread.invoke(() -> initialInventory = Collections.emptyMap());
    }

    public void onWidgetLoad(WidgetLoaded event) {
        if (event.getGroupId() != InterfaceID.SHARED_BANK)
            return;

        clientThread.invokeLater(() -> {
            ItemContainer inv = getInventory();
            if (inv == null)
                return false;

            initialInventory = reduce(inv.getItems());
            return true;
        });
    }

    public void onWidgetClose(WidgetClosed event) {
        if (event.getGroupId() != InterfaceID.LOADING_ICON_MODAL)
            return;

        Widget widget = client.getWidget(InterfaceID.LoadingIconModal.TEXT);
        if (widget == null)
            return;

        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (enabled && StringUtils.containsIgnoreCase(widget.getText(), "Saving")) {
            ItemContainer inv = getInventory();
            if (inv != null) {
                Map<Integer, Integer> updatedInventory = reduce(inv.getItems());
                Map<Integer, Integer> delta = computeDifference(initialInventory, updatedInventory);
                if (!delta.isEmpty()) {
                    handleNotify(delta, profiles);
                }
            }
        }

        this.reset();
    }

    private void handleNotify(Map<Integer, Integer> inventoryChanges, ProfileRuntimeSnapshot profiles) {
        // Calculate transaction information
        List<SerializedItemStack> deposits = new ArrayList<>();
        List<SerializedItemStack> withdrawals = new ArrayList<>();
        long debits = 0, credits = 0;
        for (Map.Entry<Integer, Integer> entry : inventoryChanges.entrySet()) {
            int diff = entry.getValue(); // positive=withdraw, negative=deposit
            SerializedItemStack item = ItemUtils.stackFromItem(itemManager, entry.getKey(), Math.abs(diff));
            long stackPrice = item.getTotalPrice();
            if (diff < 0) {
                deposits.add(item);
                debits += stackPrice;
            } else {
                withdrawals.add(item);
                credits += stackPrice;
            }
        }
        long netValue = debits - credits;

        // Sort lists so more valuable item transactions are at the top
        Comparator<SerializedItemStack> valuable = Comparator.comparingLong(SerializedItemStack::getTotalPrice).reversed();
        deposits.sort(valuable);
        withdrawals.sort(valuable);

        TransactionDetails details = new TransactionDetails(
            deposits, withdrawals, debits, credits, netValue, client.getLocalPlayer().getName(), getGroupName());

        // Fire notification (delayed by a tick for screenshotHideChat reliability)
        clientThread.invokeAtTickEnd(() -> {
            if (profiles.isProfilesConfigured()) {
                NotificationBody<?> outbound = null;
                for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
                    if (!isProfileEnabled(profile) || !meetsMinimum(details, profile)) continue;
                    NotificationBody<?> body = createBody(profile, details);
                    if (deliver(profile, profile.groupStorageWebhook(),
                        profile.groupStorageSendImage(), body) && outbound == null) outbound = body;
                }
                if (outbound != null) publishPluginMessage(outbound);
                return;
            }

            if (meetsMinimum(details, config)) {
                createMessage(config.groupStorageSendImage(), createBody(config, details));
            }
        });
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, TransactionDetails details) {
        // Convert lists to strings
        BiFunction<Collection<SerializedItemStack>, String, String> formatItems = (items, linePrefix) -> {
            if (items.isEmpty()) return EMPTY_TRANSACTION;
            return items.stream()
                .map(n -> ItemUtils.formatStack(n, deliveryConfig.groupStorageIncludePrice()))
                .collect(Collectors.joining('\n' + linePrefix, linePrefix, ""));
        };
        String depositString = formatItems.apply(details.deposits, "+ ");
        String withdrawalString = formatItems.apply(details.withdrawals, "- ");

        // Build content
        String content = StringUtils.replaceEach(deliveryConfig.groupStorageNotifyMessage(),
            new String[] { "%USERNAME%", "%DEPOSITED%", "%WITHDRAWN%" },
            new String[] { details.playerName, depositString, withdrawalString }
        );
        Template formattedText = Template.builder()
            .template("$s$")
            .replacementBoundary("$")
            .replacement("$s$", Replacements.ofBlock("diff", content))
            .build();

        // Populate metadata
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            details.deposits,
            details.withdrawals,
            details.netValue,
            deliveryConfig.groupStorageIncludeClan() ? details.groupName : null,
            deliveryConfig.groupStorageIncludePrice()
        );

        return NotificationBody.builder()
            .type(NotificationType.GROUP_STORAGE)
            .text(formattedText)
            .playerName(details.playerName)
            .extra(extra)
            .build();
    }

    private boolean meetsMinimum(TransactionDetails details, PigeonConfig deliveryConfig) {
        return details.debits >= deliveryConfig.groupStorageMinValue()
            || details.credits >= deliveryConfig.groupStorageMinValue();
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyGroupStorage() && profileEligibilityService.isEligible(profile);
    }

    /**
     * @return the name of the ironman group
     */
    private String getGroupName() {
        ClanChannel channel = client.getClanChannel(ClanID.GROUP_IRONMAN);
        return channel != null ? channel.getName() : null;
    }

    @lombok.Value
    private static class TransactionDetails {
        List<SerializedItemStack> deposits;
        List<SerializedItemStack> withdrawals;
        long debits;
        long credits;
        long netValue;
        String playerName;
        String groupName;
    }

    /**
     * Upon opening the group's shared storage, the inventory is replaced
     * by a temporary container ({@link InventoryID#INV_PLAYER_TEMP}),
     * which reflects intermediate changes before the transaction is committed.
     * We prefer reading from this "fake" inventory when available.
     * On storage open, it is a server-validated copy of the local inventory.
     * On save, it has modifications that may not yet be reflected in the real inventory.
     *
     * @return the player's inventory
     */
    private ItemContainer getInventory() {
        ItemContainer inv = client.getItemContainer(InventoryID.INV_PLAYER_TEMP);
        return inv != null ? inv : client.getItemContainer(InventoryID.INV);
    }

    /**
     * @param items array of items (e.g., in the player's inventory)
     * @return mappings of item id to total quantity of the item (across stacks)
     */
    private Map<Integer, Integer> reduce(Item[] items) {
        return Arrays.stream(items)
            .filter(Objects::nonNull)
            .filter(item -> item.getId() >= 0)
            .filter(item -> item.getQuantity() > 0)
            .collect(Collectors.toMap(i -> ItemUtils.canonicalizeItem(itemManager, i.getId()), Item::getQuantity, Integer::sum));
    }

    /**
     * @param before the reduced item mappings when the group storage was first opened
     * @param after  the reduced item mappings after the save operation
     * @param <K>    generic key to merge the maps (always Integer in this notifier)
     * @return mappings of item id to change in quantity, excluding items with no change
     */
    private static <K> Map<K, Integer> computeDifference(Map<K, Integer> before, Map<K, Integer> after) {
        Map<K, Integer> delta = new HashMap<>(after);
        before.forEach((id, quantity) -> delta.merge(id, -quantity, SUM));
        return delta;
    }

    static {
        SUM = (a, b) -> {
            int sum = a + b;
            return sum != 0 ? sum : null;
        };
    }
}
