package pigeon.notifiers;

import com.google.gson.JsonPrimitive;
import com.google.inject.testing.fieldbinder.Bind;
import pigeon.PigeonConfig;
import pigeon.domain.AccountType;
import pigeon.message.NotificationBody;
import pigeon.message.NotificationType;
import pigeon.message.templating.Replacements;
import pigeon.message.templating.Template;
import pigeon.notifiers.data.GroupStorageNotificationData;
import pigeon.notifiers.data.SerializedItemStack;
import pigeon.profiles.ConfigProfileRepository;
import pigeon.profiles.PigeonProfile;
import pigeon.profiles.ProfileWebhooks;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pigeon.notifiers.GroupStorageNotifier.EMPTY_TRANSACTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupStorageNotifierTest extends MockedNotifierTest {

    private static final String GROUP_NAME = "Dink QA";
    private static final int OPAL_PRICE = 600;
    private static final int RUBY_PRICE = 900;
    private static final int TUNA_PRICE = 100;
    private static final WidgetLoaded LOAD_EVENT;
    private static final WidgetClosed CLOSE_EVENT;

    @Bind
    @InjectMocks
    GroupStorageNotifier notifier;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // init config mocks
        when(config.discordRichEmbeds()).thenReturn(true);
        when(config.notifyGroupStorage()).thenReturn(true);
        when(config.groupStorageSendImage()).thenReturn(false);
        when(config.groupStorageIncludeClan()).thenReturn(true);
        when(config.groupStorageIncludePrice()).thenReturn(true);
        when(config.groupStorageNotifyMessage())
            .thenReturn("%USERNAME% has deposited:\n%DEPOSITED%\n\n%USERNAME% has withdrawn:\n%WITHDRAWN%");

        // init item mocks
        mockItem(ItemID.FAKE_COINS, 1, "Coins");
        mockItem(ItemID.OPAL, OPAL_PRICE, "Opal");
        mockItem(ItemID.RUBY, RUBY_PRICE, "Ruby");
        mockItem(ItemID.TUNA, TUNA_PRICE, "Tuna");
        mockItem(ItemID.DEADMAN_STARTER_TUNA, TUNA_PRICE, "Tuna");
        when(itemManager.canonicalize(ItemID.DEADMAN_STARTER_TUNA)).thenReturn(ItemID.TUNA);

        // init group mock
        when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(AccountType.HARDCORE_GROUP_IRONMAN.ordinal());
        ClanChannel channel = mock(ClanChannel.class);
        when(channel.getName()).thenReturn(GROUP_NAME);
        when(client.getClanChannel(ClanID.GROUP_IRONMAN)).thenReturn(channel);
    }

    @Test
    void testNotify() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.singletonList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby")),
            Collections.singletonList(new SerializedItemStack(ItemID.OPAL, 1, OPAL_PRICE, "Opal")),
            RUBY_PRICE - OPAL_PRICE,
            GROUP_NAME,
            true
        );

        verifyNotification(extra, "+ 1 x Ruby (" + RUBY_PRICE + ")", "- 1 x Opal (" + OPAL_PRICE + ")");
    }

    @Test
    void testNotifyTwoProfilesFromOneInventoryDelta() {
        PigeonProfile clan = groupStorageProfile(
            "Clan", true, 500, true, true,
            "Clan %DEPOSITED% / %WITHDRAWN%", "https://example.com/clan");
        PigeonProfile friends = groupStorageProfile(
            "Friends", true, 800, false, false,
            "Friends %DEPOSITED% / %WITHDRAWN%", "https://example.com/friends");
        PigeonProfile expensive = groupStorageProfile(
            "Expensive", true, 901, true, true,
            "Expensive", "https://example.com/expensive");
        PigeonProfile disabled = groupStorageProfile(
            "Disabled", false, 0, true, true,
            "Disabled", "https://example.com/disabled");
        mockStoredProfiles(clan, friends, expensive, disabled);

        mockContainer(new Item[] { new Item(ItemID.RUBY, 1) });
        notifier.onWidgetLoad(LOAD_EVENT);
        mockContainer(new Item[] { new Item(ItemID.OPAL, 1) });
        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        ArgumentCaptor<PigeonConfig> configs = ArgumentCaptor.forClass(PigeonConfig.class);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationBody> bodies = ArgumentCaptor.forClass(NotificationBody.class);
        verify(messageHandler, times(2)).createMessage(
            configs.capture(), urls.capture(), anyBoolean(), bodies.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
            List.of("https://example.com/clan", "https://example.com/friends"), urls.getAllValues());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Clan + 1 x Ruby (900) / - 1 x Opal (600)",
            bodies.getAllValues().get(0).getText().evaluate(false));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Friends + 1 x Ruby / - 1 x Opal",
            bodies.getAllValues().get(1).getText().evaluate(false));

        GroupStorageNotificationData clanExtra = (GroupStorageNotificationData) bodies.getAllValues().get(0).getExtra();
        GroupStorageNotificationData friendsExtra = (GroupStorageNotificationData) bodies.getAllValues().get(1).getExtra();
        org.junit.jupiter.api.Assertions.assertEquals(GROUP_NAME, clanExtra.getGroupName());
        org.junit.jupiter.api.Assertions.assertTrue(clanExtra.isIncludePrice());
        org.junit.jupiter.api.Assertions.assertNull(friendsExtra.getGroupName());
        org.junit.jupiter.api.Assertions.assertFalse(friendsExtra.isIncludePrice());
    }

    @Test
    void testNotifyNonePrice() {
        when(config.groupStorageIncludePrice()).thenReturn(false);

        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 2), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.singletonList(new SerializedItemStack(ItemID.RUBY, 2, RUBY_PRICE, "Ruby")),
            Collections.singletonList(new SerializedItemStack(ItemID.OPAL, 1, OPAL_PRICE, "Opal")),
            (2*RUBY_PRICE) - OPAL_PRICE,
            GROUP_NAME,
            false
        );

        verifyNotification(extra, "+ 2 x Ruby", "- 1 x Opal");
    }

    @Test
    void testNotifyCoins() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.COINS, 100) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.CERT_ROLL, 1000) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.emptyList(),
            Collections.singletonList(new SerializedItemStack(ItemID.FAKE_COINS, 900, 1, "Coins")),
            -900,
            GROUP_NAME,
            true
        );

        verifyNotification(extra, EMPTY_TRANSACTION, "- 900 x Coins (900)");
    }

    @Test
    void testNotifyStackable() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 2), new Item(ItemID.FAKE_COINS, 100), new Item(ItemID.COINS, 150) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.RUBY, 1), new Item(ItemID.CERT_ROLL, 1000), new Item(ItemID.MAGICTRAINING_COINS, 50) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.emptyList(),
            Collections.singletonList(new SerializedItemStack(ItemID.FAKE_COINS, 800, 1, "Coins")),
            -800,
            GROUP_NAME,
            true
        );

        verifyNotification(extra, EMPTY_TRANSACTION, "- 800 x Coins (800)");
    }

    @Test
    void testNotifyMultipleDebit() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        mockContainer(new Item[0]);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Arrays.asList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby"), new SerializedItemStack(ItemID.OPAL, 1, OPAL_PRICE, "Opal")),
            Collections.emptyList(),
            RUBY_PRICE + OPAL_PRICE,
            GROUP_NAME,
            true
        );

        verifyNotification(extra, "+ 1 x Ruby (" + RUBY_PRICE + ")\n+ 1 x Opal (" + OPAL_PRICE + ")", EMPTY_TRANSACTION);
    }

    @Test
    void testNotifyMultipleCredit() {
        // mock initial inventory state
        mockContainer(new Item[0]);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.emptyList(),
            Arrays.asList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby"), new SerializedItemStack(ItemID.OPAL, 1, OPAL_PRICE, "Opal")),
            -(RUBY_PRICE + OPAL_PRICE),
            GROUP_NAME,
            true
        );

        verifyNotification(extra, EMPTY_TRANSACTION, "- 1 x Ruby (" + RUBY_PRICE + ")\n- 1 x Opal (" + OPAL_PRICE + ")");
    }

    @Test
    void testNotifyNoted() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.TUNA, 2) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.DEADMAN_STARTER_TUNA, 3) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.emptyList(),
            Collections.singletonList(new SerializedItemStack(ItemID.TUNA, 1, TUNA_PRICE, "Tuna")),
            -TUNA_PRICE,
            GROUP_NAME,
            true
        );

        verifyNotification(extra, EMPTY_TRANSACTION, "- 1 x Tuna (" + TUNA_PRICE + ")");
    }

    @Test
    void testWithoutGroupName() {
        // update config mocks
        when(config.groupStorageIncludeClan()).thenReturn(false);

        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // verify notification message
        GroupStorageNotificationData extra = new GroupStorageNotificationData(
            Collections.singletonList(new SerializedItemStack(ItemID.RUBY, 1, RUBY_PRICE, "Ruby")),
            Collections.singletonList(new SerializedItemStack(ItemID.OPAL, 1, OPAL_PRICE, "Opal")),
            RUBY_PRICE - OPAL_PRICE,
            null,
            true
        );

        verifyNotification(extra, "+ 1 x Ruby (" + RUBY_PRICE + ")", "- 1 x Opal (" + OPAL_PRICE + ")");
    }

    @Test
    void testIgnoreNoted() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.TUNA, 2) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.DEADMAN_STARTER_TUNA, 2) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreValue() {
        // update config mocks
        when(config.groupStorageMinValue()).thenReturn(RUBY_PRICE + 1);

        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnoreZero() {
        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.RUBY, 1) };
        mockContainer(updatedItems);

        mockSaveWidget();
        notifier.onWidgetClose(CLOSE_EVENT);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    @Test
    void testIgnore() {
        // update mocks
        when(config.notifyGroupStorage()).thenReturn(false);

        // mock initial inventory state
        Item[] initialItems = { new Item(ItemID.RUBY, 1), new Item(ItemID.TUNA, 1) };
        mockContainer(initialItems);
        notifier.onWidgetLoad(LOAD_EVENT);

        // mock updated inventory
        Item[] updatedItems = { new Item(ItemID.TUNA, 1), new Item(ItemID.OPAL, 1) };
        mockContainer(updatedItems);
        notifier.onWidgetClose(CLOSE_EVENT);

        // ensure no notification
        verify(messageHandler, never()).createMessage(any(), anyBoolean(), any());
    }

    private void verifyNotification(GroupStorageNotificationData extra, String deposited, String withdrawn) {
        String text = String.format(
            "%s has deposited:\n%s\n\n%s has withdrawn:\n%s",
            PLAYER_NAME, deposited, PLAYER_NAME, withdrawn
        );

        verifyCreateMessage(
            PRIMARY_WEBHOOK_URL,
            false,
            NotificationBody.builder()
                .text(
                    Template.builder()
                        .template("{{x}}")
                        .replacement("{{x}}", Replacements.ofBlock("diff", text))
                        .build()
                )
                .extra(extra)
                .type(NotificationType.GROUP_STORAGE)
                .playerName(PLAYER_NAME)
                .build()
        );
    }

    private void mockContainer(Item[] items) {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getItems()).thenReturn(items);
        when(client.getItemContainer(InventoryID.INV_PLAYER_TEMP)).thenReturn(container);
    }

    private PigeonProfile groupStorageProfile(String name, boolean enabled, int minimum,
                                               boolean includeClan, boolean includePrice,
                                               String message, String webhook) {
        return PigeonProfile.builder()
            .schemaVersion(PigeonProfile.CURRENT_SCHEMA_VERSION)
            .id(UUID.randomUUID())
            .name(name)
            .enabled(enabled)
            .settings(Map.of(
                "groupStorageEnabled", new JsonPrimitive(true),
                "groupStorageSendImage", new JsonPrimitive(false),
                "groupStorageMinValue", new JsonPrimitive(minimum),
                "groupStorageIncludeClan", new JsonPrimitive(includeClan),
                "groupStorageIncludePrice", new JsonPrimitive(includePrice),
                "groupStorageNotifyMessage", new JsonPrimitive(message)
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

    private void mockSaveWidget() {
        Widget widget = mock(Widget.class);
        when(client.getWidget(WidgetUtil.packComponentId(InterfaceID.LOADING_ICON_MODAL, 1))).thenReturn(widget);
        when(widget.getText()).thenReturn("Saving...");
    }

    static {
        LOAD_EVENT = new WidgetLoaded();
        LOAD_EVENT.setGroupId(InterfaceID.SHARED_BANK);
        CLOSE_EVENT = new WidgetClosed(InterfaceID.LOADING_ICON_MODAL, WidgetModalMode.MODAL_NOCLICKTHROUGH, true);
    }
}
