package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.notifiers.data.TradeNotificationData;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeNotifierTest extends MockedNotifierTest {
    private static final String COUNTERPARTY = "Billy";

    private static final int OPAL_PRICE = 600;
    private static final int RUBY_PRICE = 900;

    @Bind
    @InjectMocks
    TradeNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.notifyTrades()).thenReturn(true);
        when(config.tradeMinValue()).thenReturn(2000);
        when(config.tradeNotifyMessage()).thenReturn("%USERNAME% traded with %COUNTERPARTY%");

        // init item mocks
        mockItem(ItemID.OPAL, OPAL_PRICE, "Opal");
        mockItem(ItemID.RUBY, RUBY_PRICE, "Ruby");
    }

    @Test
    void testNotify() {
        // update mocks
        when(client.getVarcStrValue(TradeNotifier.TRADE_COUNTERPARTY_VAR)).thenReturn(COUNTERPARTY);

        ItemContainer tradeContainer = mock(ItemContainer.class);
        Item[] tradeItems = {new Item(ItemID.OPAL, 2)};
        when(tradeContainer.getItems()).thenReturn(tradeItems);
        when(client.getItemContainer(InventoryID.TRADEOFFER)).thenReturn(tradeContainer);

        ItemContainer otherContainer = mock(ItemContainer.class);
        Item[] otherItems = {new Item(ItemID.RUBY, 1)};
        when(otherContainer.getItems()).thenReturn(otherItems);
        when(client.getItemContainer(TradeNotifier.INV_TRADE_OTHER)).thenReturn(otherContainer);

        // fire event
        notifier.onTradeMessage(TradeNotifier.TRADE_ACCEPTED_MESSAGE);

        // verify handled
        List<SerializedItemStack> received = List.of(
            new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby")
        );
        List<SerializedItemStack> discarded = List.of(
            new SerializedItemStack(ItemID.OPAL, 2, OPAL_PRICE, "Opal")
        );
        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("%USERNAME% traded with %COUNTERPARTY%")
                        .replacement("%USERNAME%", Replacements.ofText(PLAYER_NAME))
                        .replacement("%COUNTERPARTY%", Replacements.ofLink(COUNTERPARTY, config.playerLookupService().getPlayerUrl(COUNTERPARTY)))
                        .build()
                )
                .extra(new TradeNotificationData(COUNTERPARTY, received, discarded, RUBY_PRICE, 2 * OPAL_PRICE))
                .type(NotificationType.TRADE)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    @Test
    void testNotifyTwoProfilesFromOneTradeCapture() {
        PigeonProfile clan = tradeProfile(
            "Clan", true, 2_000, "Clan: %COUNTERPARTY% %IN_VALUE%/%OUT_VALUE%",
            "https://example.com/clan");
        PigeonProfile friends = tradeProfile(
            "Friends", true, 2_100, "Friends: %COUNTERPARTY% %IN_VALUE%/%OUT_VALUE%",
            "https://example.com/friends");
        PigeonProfile expensive = tradeProfile(
            "Expensive", true, 2_101, "Expensive", "https://example.com/expensive");
        PigeonProfile disabled = tradeProfile(
            "Disabled", false, 0, "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, expensive, disabled);

        when(client.getVarcStrValue(TradeNotifier.TRADE_COUNTERPARTY_VAR)).thenReturn(COUNTERPARTY);
        ItemContainer tradeContainer = mock(ItemContainer.class);
        when(tradeContainer.getItems()).thenReturn(new Item[] { new Item(ItemID.OPAL, 2) });
        when(client.getItemContainer(InventoryID.TRADEOFFER)).thenReturn(tradeContainer);
        ItemContainer otherContainer = mock(ItemContainer.class);
        when(otherContainer.getItems()).thenReturn(new Item[] { new Item(ItemID.RUBY, 1) });
        when(client.getItemContainer(TradeNotifier.INV_TRADE_OTHER)).thenReturn(otherContainer);

        notifier.onTradeMessage(TradeNotifier.TRADE_ACCEPTED_MESSAGE);

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan: Billy 900/1,200", bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends: Billy 900/1,200", bodies.getAllValues().get(1).getText().evaluate(false));
    }

    @Test
    void testIgnoreValue() {
        // update mocks
        when(client.getVarcStrValue(TradeNotifier.TRADE_COUNTERPARTY_VAR)).thenReturn(COUNTERPARTY);

        ItemContainer tradeContainer = mock(ItemContainer.class);
        Item[] tradeItems = {new Item(ItemID.OPAL, 1)};
        when(tradeContainer.getItems()).thenReturn(tradeItems);
        when(client.getItemContainer(InventoryID.TRADEOFFER)).thenReturn(tradeContainer);

        ItemContainer otherContainer = mock(ItemContainer.class);
        Item[] otherItems = {new Item(ItemID.RUBY, 1)};
        when(otherContainer.getItems()).thenReturn(otherItems);
        when(client.getItemContainer(TradeNotifier.INV_TRADE_OTHER)).thenReturn(otherContainer);

        // fire event
        notifier.onTradeMessage(TradeNotifier.TRADE_ACCEPTED_MESSAGE);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testDisabled() {
        // update mocks
        when(config.notifyTrades()).thenReturn(false);
        when(client.getVarcStrValue(TradeNotifier.TRADE_COUNTERPARTY_VAR)).thenReturn(COUNTERPARTY);

        ItemContainer tradeContainer = mock(ItemContainer.class);
        Item[] tradeItems = {new Item(ItemID.OPAL, 2)};
        when(tradeContainer.getItems()).thenReturn(tradeItems);
        when(client.getItemContainer(InventoryID.TRADEOFFER)).thenReturn(tradeContainer);

        ItemContainer otherContainer = mock(ItemContainer.class);
        Item[] otherItems = {new Item(ItemID.RUBY, 1)};
        when(otherContainer.getItems()).thenReturn(otherItems);
        when(client.getItemContainer(TradeNotifier.INV_TRADE_OTHER)).thenReturn(otherContainer);

        // fire event
        notifier.onTradeMessage(TradeNotifier.TRADE_ACCEPTED_MESSAGE);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private PigeonProfile tradeProfile(String name, boolean enabled, int minimum,
                                       String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "notifyTrades", new JsonPrimitive(true),
                "tradeSendImage", new JsonPrimitive(false),
                "tradeMinValue", new JsonPrimitive(minimum),
                "tradeNotifyMessage", new JsonPrimitive(message)
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

}
