package pigeon.notifiers;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import pigeon.message.Embed;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.GrandExchangeNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.PigeonProfileConfig;
import pigeon.profiles.ProfileEligibilityService;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;
import pigeon.util.ConfigUtil;
import pigeon.util.ItemUtils;
import pigeon.util.SerializedOffer;
import pigeon.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.grandexchange.GrandExchangePlugin;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class GrandExchangeNotifier extends BaseNotifier {
    private static final Set<Integer> TAX_EXEMPT_ITEMS;
    private static final int LOGIN_DELAY = 2;
    private static final String RL_GE_PLUGIN_NAME = GrandExchangePlugin.class.getSimpleName().toLowerCase();
    private final AtomicInteger initTicks = new AtomicInteger();
    private static final String LEGACY_PROFILE_KEY = "legacy";
    private final Map<Integer, Map<String, Instant>> progressNotificationTimeBySlot = new HashMap<>();

    @Inject
    private ClientThread clientThread;

    @Inject
    private Gson gson;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ProfileRuntimeService profileRuntimeService;

    @Inject
    private ProfileEligibilityService profileEligibilityService;

    @Override
    public boolean isEnabled() {
        return config.notifyGrandExchange() && super.isEnabled();
    }

    @Override
    protected String getWebhookUrl() {
        return config.grandExchangeWebhook();
    }

    public void onAccountChange() {
        progressNotificationTimeBySlot.clear();
    }

    public void onGameStateChange(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN)
            initTicks.set(LOGIN_DELAY);
    }

    public void onTick() {
        initTicks.updateAndGet(i -> Math.max(i - 1, 0));
    }

    public void onOfferChange(int slot, GrandExchangeOffer offer) {
        boolean loginReplay = initTicks.get() > 0;
        if (shouldHandle(slot, offer, loginReplay)) {
            clientThread.invoke(() -> {
                if (initTicks.get() > 0) {
                    return false; // retry later
                }

                ProfileRuntimeSnapshot profiles = profileRuntimeService.snapshot();
                boolean enabled = profiles.isProfilesConfigured()
                    ? profiles.getEnabledProfiles().stream().anyMatch(this::isProfileEnabled)
                    : isEnabled();
                if (!enabled) {
                    return true; // terminate execution since notifier is disabled
                }

                var welcome = client.getWidget(InterfaceID.WelcomeScreen.CONTENT);
                if (welcome != null && !welcome.isHidden()) {
                    return false; // wait until user has passed welcome screen
                }

                handleNotify(slot, offer, profiles, loginReplay);
                return true;
            });
        }
    }

    private void handleNotify(int slot, GrandExchangeOffer offer, ProfileRuntimeSnapshot profiles,
                              boolean loginReplay) {
        log.debug("Notifying for slot={}, item={}, quantity={}, targetPrice={}, spent={}, state={}",
            slot, offer.getItemId(), offer.getQuantitySold(), offer.getPrice(), offer.getSpent(), offer.getState());

        if (profiles.isProfilesConfigured()) {
            List<PigeonProfileConfig> recipients = profiles.getEnabledProfiles().stream()
                .filter(this::isProfileEnabled)
                .filter(profile -> shouldNotify(slot, offer, profile,
                    profile.getProfile().getId().toString(), loginReplay))
                .collect(java.util.stream.Collectors.toList());
            if (recipients.isEmpty()) return;

            OfferDetails details = getOfferDetails(slot, offer);
            NotificationBody<?> outbound = null;
            for (PigeonProfileConfig profile : recipients) {
                NotificationBody<?> body = createBody(profile, offer, details);
                if (deliver(profile, profile.grandExchangeWebhook(),
                    profile.grandExchangeSendImage(), body) && outbound == null) outbound = body;
            }
            if (outbound != null) publishPluginMessage(outbound);
            return;
        }

        if (shouldNotify(slot, offer, config, LEGACY_PROFILE_KEY, loginReplay)) {
            createMessage(config.grandExchangeSendImage(), createBody(config, offer, getOfferDetails(slot, offer)));
        }
    }

    private OfferDetails getOfferDetails(int slot, GrandExchangeOffer offer) {

        ItemComposition comp = itemManager.getItemComposition(offer.getItemId());
        SerializedItemStack item = new SerializedItemStack(offer.getItemId(), offer.getQuantitySold(), getUnitPrice(offer), comp.getMembersName());
        long marketPrice = ItemUtils.getPrice(itemManager, offer.getItemId());
        OfferType type = getType(offer.getState());
        Long tax = type == OfferType.SELL ? calculateTax(item.getPriceEach(), item.getQuantity(), item.getId()) : null;

        return new OfferDetails(slot, item, marketPrice, type, tax);
    }

    private NotificationBody<?> createBody(pigeon.PigeonConfig deliveryConfig, GrandExchangeOffer offer,
                                            OfferDetails details) {
        List<Embed> embeds;
        if (deliveryConfig.grandExchangeSendImage() || !deliveryConfig.discordRichEmbeds()) {
            embeds = Collections.emptyList();
        } else {
            embeds = ItemUtils.buildEmbeds(new int[] { offer.getItemId() });
        }

        String playerName = Utils.getPlayerName(client);
        Template message = Template.builder()
            .template(deliveryConfig.grandExchangeNotifyMessage())
            .replacementBoundary("%")
            .replacement("%USERNAME%", Replacements.ofText(playerName))
            .replacement("%TYPE%", Replacements.ofText(details.type.getDisplayName()))
            .replacement("%ITEM%", ItemUtils.templateStack(details.item, true))
            .replacement("%STATUS%", Replacements.ofText(getHumanStatus(offer.getState())))
            .build();
        return NotificationBody.builder()
            .type(NotificationType.GRAND_EXCHANGE)
            .text(message)
            .embeds(embeds)
            .extra(new GrandExchangeNotificationData(details.slot + 1, offer.getState(), details.item,
                details.marketPrice, offer.getPrice(), offer.getTotalQuantity(), details.tax))
            .playerName(playerName)
            .build();
    }

    private boolean shouldHandle(int slot, GrandExchangeOffer offer, boolean justLogged) {
        // During login, we only care about offers that have been completed, and that were *not* observed by the RuneLite GE plugin
        // This makes sure we don't fire any duplicate notifications for offers that were finished while we were online
        if (justLogged) {
            // check if offer is a completion
            if (offer.getState() != GrandExchangeOfferState.BOUGHT && offer.getState() != GrandExchangeOfferState.SOLD)
                return false;

            // require GE plugin to be enabled so that observed trades are written to config
            // however: not bullet-proof since GE plugin could've been disabled during the initial trade completion
            if (ConfigUtil.isPluginDisabled(configManager, RL_GE_PLUGIN_NAME))
                return false;

            // check whether the completion has already been observed
            if (getSavedOffer(slot).filter(saved -> saved.equalsOffer(offer)).isPresent())
                return false;
        }

        return true;
    }

    private boolean shouldNotify(int slot, GrandExchangeOffer offer, pigeon.PigeonConfig deliveryConfig,
                                 String profileKey, boolean loginReplay) {
        if (!loginReplay && deliveryConfig instanceof PigeonProfileConfig
            && !isProfileEnabled((PigeonProfileConfig) deliveryConfig)) return false;
        if (!loginReplay && deliveryConfig == config && !isEnabled()) return false;

        boolean valuable = getTransactedValue(offer) >= deliveryConfig.grandExchangeMinValue();

        switch (offer.getState()) {
            case EMPTY:
                if (client.getGameState() == GameState.LOGGED_IN)
                    progressNotificationTimeBySlot.remove(slot);
                return false;

            case BOUGHT:
            case SOLD:
                progressNotificationTimeBySlot.remove(slot);
                return valuable;

            case CANCELLED_BUY:
            case CANCELLED_SELL:
                progressNotificationTimeBySlot.remove(slot);
                return valuable && deliveryConfig.grandExchangeIncludeCancelled();

            case BUYING:
            case SELLING:
                if (!valuable || offer.getQuantitySold() <= 0)
                    return false;

                if (offer.getQuantitySold() >= offer.getTotalQuantity())
                    return false; // ignore since BOUGHT/SOLD is about to occur

                int spacing = deliveryConfig.grandExchangeProgressSpacingMinutes();
                if (spacing < 0)
                    return false; // negative => no in-progress notifications allowed

                if (getSavedOffer(slot).filter(saved -> saved.equalsOffer(offer)).isPresent())
                    return false; // ignore since quantity already observed (relevant when trade limit is binding)

                // convert minutes to seconds, but treat 0 minutes as 2 seconds to workaround duplicate RL events
                long spacingSeconds = spacing > 0 ? spacing * 60L : 2L;

                Instant now = Instant.now();
                Map<String, Instant> times = progressNotificationTimeBySlot.computeIfAbsent(
                    slot, ignored -> new HashMap<>());
                Instant prior = times.get(profileKey);
                if (prior == null || Duration.between(prior, now).getSeconds() >= spacingSeconds) {
                    return times.put(profileKey, now) == prior;
                }
                return false;

            default:
                return false;
        }
    }

    private boolean isProfileEnabled(PigeonProfileConfig profile) {
        return profile.notifyGrandExchange() && profileEligibilityService.isEligible(profile);
    }

    @lombok.Value
    private static class OfferDetails {
        int slot;
        SerializedItemStack item;
        long marketPrice;
        OfferType type;
        Long tax;
    }

    private Optional<SerializedOffer> getSavedOffer(int slot) {
        return Optional.ofNullable(configManager.getRSProfileConfiguration("geoffer", String.valueOf(slot)))
            .map(json -> {
                try {
                    return gson.fromJson(json, SerializedOffer.class);
                } catch (JsonSyntaxException e) {
                    log.warn("Failed to read saved GE offer", e);
                    return null;
                }
            });
    }

    public static String getHumanStatus(GrandExchangeOfferState state) {
        switch (state) {
            case CANCELLED_BUY:
            case CANCELLED_SELL:
                return "Cancelled";

            case BUYING:
            case SELLING:
                return "In Progress";

            case BOUGHT:
            case SOLD:
                return "Completed";

            default:
                return null;
        }
    }

    private static OfferType getType(GrandExchangeOfferState state) {
        switch (state) {
            case BUYING:
            case CANCELLED_BUY:
            case BOUGHT:
                return OfferType.BUY;

            case SELLING:
            case CANCELLED_SELL:
            case SOLD:
                return OfferType.SELL;

            default:
                return null;
        }
    }

    private static long getTransactedValue(GrandExchangeOffer offer) {
        long spent = offer.getSpent();
        return spent > 0 ? spent : (long) offer.getQuantitySold() * offer.getPrice();
    }

    private static int getUnitPrice(GrandExchangeOffer offer) {
        int quantity = offer.getQuantitySold();
        int spent = offer.getSpent();
        return quantity > 0 && spent > 0 ? spent / quantity : offer.getPrice();
    }

    private static long calculateTax(int unitPrice, int quantity, int itemId) {
        // https://secure.runescape.com/m=news/grand-exchange-tax--item-sink?oldschool=1
        if (unitPrice < 50 || TAX_EXEMPT_ITEMS.contains(itemId)) {
            return 0L;
        }
        int price = Math.min(unitPrice, 250_000_000);
        int unitTax = (int) Math.floor(price * 0.02);
        return (long) unitTax * quantity;
    }

    @Getter
    @RequiredArgsConstructor
    private enum OfferType {
        BUY("bought"),
        SELL("sold");

        private final String displayName;
    }

    static {
        // https://oldschool.runescape.wiki/w/Category:Items_exempt_from_Grand_Exchange_tax
        TAX_EXEMPT_ITEMS = ImmutableSet.of(
            ItemID.CHISEL, ItemID.DIBBER, ItemID.GARDENING_TROWEL,
            ItemID.GLASSBLOWINGPIPE, ItemID.HAMMER, ItemID.NEEDLE,
            ItemID.PESTLE_AND_MORTAR, ItemID.RAKE, ItemID.POH_SAW,
            ItemID.SECATEURS, ItemID.SHEARS, ItemID.SPADE,
            ItemID.WATERING_CAN_0, ItemID.OSRS_BOND
        );
    }
}
