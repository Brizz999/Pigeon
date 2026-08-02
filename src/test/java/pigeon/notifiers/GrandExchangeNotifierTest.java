package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.message.Embed;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.GrandExchangeNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import pigeon.util.ItemUtils;
import lombok.Value;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.util.QuantityFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

public class GrandExchangeNotifierTest extends MockedNotifierTest {

    private static final int RUBY_PRICE = 900;
    private static final int OPAL_PRICE = 600;

    @Bind
    @InjectMocks
    GrandExchangeNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // config mocks
        when(config.notifyGrandExchange()).thenReturn(true);
        when(config.grandExchangeMinValue()).thenReturn(5000);
        when(config.grandExchangeProgressSpacingMinutes()).thenReturn(-1);
        when(config.grandExchangeNotifyMessage()).thenReturn("%USERNAME% %TYPE% %ITEM% on the GE");

        // item mocks
        mockItem(ItemID.RUBY, RUBY_PRICE, "Ruby");
        mockItem(ItemID.OPAL, OPAL_PRICE, "Opal");
    }

    @Test
    void testNotifyBuy() {
        // fire event
        Offer offer = new Offer(10, ItemID.RUBY, 10, RUBY_PRICE, 10_000, GrandExchangeOfferState.BOUGHT);
        notifier.onOfferChange(0, offer);

        // verify notification
        verifyNotification(0, offer, "bought", "Ruby", RUBY_PRICE, null);
    }

    @Test
    void testNotifySell() {
        // fire event
        Offer offer = new Offer(10, ItemID.OPAL, 10, OPAL_PRICE, 7_000, GrandExchangeOfferState.SOLD);
        notifier.onOfferChange(1, offer);

        // verify notification
        verifyNotification(1, offer, "sold", "Opal", OPAL_PRICE, 10 * 14L);
    }

    @Test
    void testNotifyTwoProfilesFromOneProgressOffer() {
        PigeonProfile clan = grandExchangeProfile(
            "Clan", true, 5_000, "Clan: %TYPE% %STATUS% %ITEM%", "https://example.com/clan");
        PigeonProfile friends = grandExchangeProfile(
            "Friends", true, 9_000, "Friends: %TYPE% %STATUS% %ITEM%", "https://example.com/friends");
        PigeonProfile expensive = grandExchangeProfile(
            "Expensive", true, 10_001, "Expensive", "https://example.com/expensive");
        PigeonProfile disabled = grandExchangeProfile(
            "Disabled", false, 0, "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, expensive, disabled);

        Offer offer = new Offer(10, ItemID.RUBY, 20, RUBY_PRICE, 10_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer);

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: bought In Progress 10 x Ruby (10K)", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: bought In Progress 10 x Ruby (10K)", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testIgnoreValue() {
        // fire event
        Offer offer = new Offer(10, ItemID.OPAL, 5, OPAL_PRICE, 3_500, GrandExchangeOfferState.SOLD);
        notifier.onOfferChange(1, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreBuying() {
        // fire event
        Offer offer = new Offer(10, ItemID.RUBY, 50, RUBY_PRICE, 10_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreSelling() {
        // fire event
        Offer offer = new Offer(10, ItemID.OPAL, 50, OPAL_PRICE, 3_500, GrandExchangeOfferState.SELLING);
        notifier.onOfferChange(1, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testNotifyPartialDifferentSlots() {
        // update config mock
        when(config.grandExchangeProgressSpacingMinutes()).thenReturn(0);

        // fire event
        Offer offer = new Offer(11, ItemID.RUBY, 50, RUBY_PRICE, 11_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(1, offer);

        // verify notification
        verifyNotification(1, offer, "bought", "Ruby", RUBY_PRICE, null);

        // fire second event without spacing
        Offer offer2 = new Offer(22, ItemID.OPAL, 60, OPAL_PRICE, 15_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(2, offer2);

        // verify second notification
        verifyNotification(2, offer2, "bought", "Opal", OPAL_PRICE, null);
    }

    @Test
    void testNotifySpacing() throws InterruptedException {
        // update config mock
        when(config.grandExchangeProgressSpacingMinutes()).thenReturn(0);

        // fire event
        Offer offer = new Offer(11, ItemID.RUBY, 50, RUBY_PRICE, 11_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer);

        // verify notification
        verifyNotification(0, offer, "bought", "Ruby", RUBY_PRICE, null);

        // allow time to pass
        Thread.sleep(2500);

        // fire second event
        Offer offer2 = new Offer(22, ItemID.RUBY, 50, RUBY_PRICE, 22_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer2);

        // verify second notification
        verifyNotification(0, offer2, "bought", "Ruby", RUBY_PRICE, null);
    }

    @Test
    void testIgnoreSpacing() {
        // update config mock
        when(config.grandExchangeProgressSpacingMinutes()).thenReturn(0);

        // fire event
        Offer offer = new Offer(10, ItemID.RUBY, 50, RUBY_PRICE, 10_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer);

        // fire second event without spacing
        Offer offer2 = new Offer(20, ItemID.RUBY, 50, RUBY_PRICE, 10_000, GrandExchangeOfferState.BUYING);
        notifier.onOfferChange(0, offer2);

        // verify first notification
        verifyNotification(0, offer, "bought", "Ruby", RUBY_PRICE, null);

        // ensure no notification for second event
        verifyNoMoreInteractions(messageHandler);
    }

    @Test
    void testNotifyCancelled() {
        // update config mock
        when(config.grandExchangeIncludeCancelled()).thenReturn(true);

        // fire event
        Offer offer = new Offer(11, ItemID.RUBY, 50, RUBY_PRICE, 11_000, GrandExchangeOfferState.CANCELLED_BUY);
        notifier.onOfferChange(0, offer);

        // verify notification
        verifyNotification(0, offer, "bought", "Ruby", RUBY_PRICE, null);
    }

    @Test
    void testIgnoreCancelledValue() {
        // update config mock
        when(config.grandExchangeIncludeCancelled()).thenReturn(true);

        // fire event
        Offer offer = new Offer(2, ItemID.RUBY, 50, RUBY_PRICE, 2_000, GrandExchangeOfferState.CANCELLED_BUY);
        notifier.onOfferChange(0, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreCancelled() {
        // fire event
        Offer offer = new Offer(11, ItemID.RUBY, 50, RUBY_PRICE, 11_000, GrandExchangeOfferState.CANCELLED_BUY);
        notifier.onOfferChange(0, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // update config mock
        when(config.notifyGrandExchange()).thenReturn(false);

        // fire event
        Offer offer = new Offer(10, ItemID.RUBY, 10, RUBY_PRICE, 10_000, GrandExchangeOfferState.BOUGHT);
        notifier.onOfferChange(0, offer);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private void verifyNotification(int slot, Offer offer, String type, String itemName, long marketPrice, Long tax) {
        SerializedItemStack item = new SerializedItemStack(offer.getItemId(), offer.getQuantitySold(), offer.getSpent() / offer.getQuantitySold(), itemName);
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .type(NotificationType.GRAND_EXCHANGE)
                .text(
                    Template.builder()
                        .template(PLAYER_NAME + " {{type}} {{quantity}} x {{item}} ({{value}}) on the GE")
                        .replacement("{{type}}", Replacements.ofText(type))
                        .replacement("{{quantity}}", Replacements.ofText(String.valueOf(offer.getQuantitySold())))
                        .replacement("{{item}}", Replacements.ofWiki(itemName))
                        .replacement("{{value}}", Replacements.ofText(QuantityFormatter.quantityToStackSize(item.getTotalPrice())))
                        .build()
                )
                .embeds(Collections.singletonList(Embed.ofImage(ItemUtils.getItemImageUrl(item.getId()))))
                .extra(new GrandExchangeNotificationData(slot + 1, offer.getState(), item, marketPrice, offer.getPrice(), offer.getTotalQuantity(), tax))
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    private PigeonProfile grandExchangeProfile(String name, boolean enabled, int minimum,
                                                String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "notifyGrandExchange", new JsonPrimitive(true),
                "grandExchangeSendImage", new JsonPrimitive(false),
                "grandExchangeIncludeCancelled", new JsonPrimitive(false),
                "grandExchangeMinValue", new JsonPrimitive(minimum),
                "grandExchangeProgressSpacingMinutes", new JsonPrimitive(0),
                "grandExchangeNotifyMessage", new JsonPrimitive(message)
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

    @Value
    private static class Offer implements GrandExchangeOffer {
        int quantitySold;
        int itemId;
        int totalQuantity;
        int price;
        int spent;
        GrandExchangeOfferState state;
    }
}
