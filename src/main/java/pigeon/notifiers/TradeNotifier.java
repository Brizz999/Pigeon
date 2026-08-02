package pigeon.notifiers;

import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.notifiers.data.TradeNotificationData;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ItemUtils;
import pigeon.util.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.annotations.VarCStr;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.QuantityFormatter;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Singleton
public class TradeNotifier extends BaseNotifier {
    @VisibleForTesting
    public static final String TRADE_ACCEPTED_MESSAGE = "Accepted trade.";
    /**
     * @see <a href="https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bclientscript%2Ctrade_partner_set%5D.cs2#L3">CS2 Reference</a>
     */
    @VisibleForTesting
    static final @VarCStr int TRADE_COUNTERPARTY_VAR = 357;

    @VisibleForTesting
    static final int INV_TRADE_OTHER = InventoryID.TRADEOFFER | 0x8000;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DrawManager drawManager;

    @Inject
    private ImageCapture imageCapture;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    private final AtomicReference<Image> image = new AtomicReference<>();

    @Override
    public boolean isEnabled() {
        return config.notifyTrades() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.tradeWebhook();
    }

    public void reset() {
        image.lazySet(null);
    }

    public void onTradeMessage(String message) {
        ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
        boolean enabled = profiles.isProfilesConfigured()
            ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
            : isEnabled();
        if (!TRADE_ACCEPTED_MESSAGE.equals(message) || !enabled) {
            this.reset();
            return;
        }

        ItemContainer tradeInv = client.getItemContainer(InventoryID.TRADEOFFER);
        ItemContainer otherInv = client.getItemContainer(INV_TRADE_OTHER);
        if (tradeInv == null && otherInv == null) {
            log.debug("Could not find traded items!");
            this.reset();
            return;
        }
        Item[] trade = tradeInv != null ? tradeInv.getItems() : new Item[0];
        Item[] other = otherInv != null ? otherInv.getItems() : new Item[0];
        long receiveValue = getTotalValue(other);
        long giveValue = getTotalValue(trade);
        if (!profiles.isProfilesConfigured() && receiveValue + giveValue < config.tradeMinValue()) {
            this.reset();
            return;
        }
        List<SerializedItemStack> received = getItems(other);
        List<SerializedItemStack> disbursed = getItems(trade);

        String localPlayer = client.getLocalPlayer().getName();
        String counterparty = Utils.sanitize(client.getVarcStrValue(TRADE_COUNTERPARTY_VAR));
        TradeNotificationData extra = new TradeNotificationData(
            counterparty, received, disbursed, receiveValue, giveValue);
        if (profiles.isProfilesConfigured()) {
            NotificationBody<?> outbound = null;
            for (PigeonProfileConfig profile : profiles.getEnabledProfiles()) {
                if (!isProfileEnabled(profile)
                    || receiveValue + giveValue < profile.tradeMinValue()) continue;
                NotificationBody<?> body = createBody(
                    profile, localPlayer, counterparty, receiveValue, giveValue, extra);
                if (deliver(profile, profile.tradeWebhook(), profile.tradeSendImage(), body)
                    && outbound == null) outbound = body;
            }
            if (outbound != null) publishPluginMessage(outbound);
            reset();
            return;
        }

        createMessage(config.tradeSendImage(), createBody(
            config, localPlayer, counterparty, receiveValue, giveValue, extra));
        this.reset();
    }

    private NotificationBody<?> createBody(PigeonConfig deliveryConfig, String localPlayer,
                                            String counterparty, long receiveValue, long giveValue,
                                            TradeNotificationData extra) {
        Template content = Template.builder()
            .template(deliveryConfig.tradeNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(localPlayer))
            .replacement("%COUNTERPARTY%", Replacements.ofLink(
                counterparty, deliveryConfig.playerLookupService().getPlayerUrl(counterparty)))
            .replacement("%IN_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(receiveValue)))
            .replacement("%OUT_VALUE%", Replacements.ofText(QuantityFormatter.quantityToStackSize(giveValue)))
            .build();

        return NotificationBody.builder()
            .text(content)
            .extra(extra)
            .playerName(localPlayer)
            .screenshotOverride(image.get())
            .type(NotificationType.TRADE)
            .build();
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyTrades() && profileEligibilityService.isEligible(profile);
    }

    public void onWidgetLoad(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.TRADECONFIRM) {
            Utils.captureScreenshot(client, clientThread, drawManager, imageCapture, executor, config, image::set);
        }
    }

    public void onWidgetClose(WidgetClosed event) {
        // relevant when local player declines a trade since no chat message occurs
        if (event.getGroupId() == InterfaceID.TRADECONFIRM) {
            clientThread.invokeAtTickEnd(this::reset);
        }
    }

    private long getTotalValue(Item[] items) {
        long v = 0;
        for (Item item : items) {
            v += ItemUtils.getPrice(itemManager, item.getId()) * item.getQuantity();
        }
        return v;
    }

    private List<SerializedItemStack> getItems(Item[] items) {
        if (items.length == 0) {
            return Collections.emptyList();
        }
        Map<Integer, Integer> quantityById = new HashMap<>(items.length * 4 / 3);
        for (Item item : items) {
            quantityById.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        List<SerializedItemStack> stacks = new ArrayList<>(quantityById.size());
        quantityById.forEach((id, quantity) -> stacks.add(ItemUtils.stackFromItem(itemManager, id, quantity)));
        return stacks;
    }
}
