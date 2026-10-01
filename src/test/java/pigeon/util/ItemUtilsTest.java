package pigeon.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pigeon.domain.LootCriteria;
import pigeon.notifiers.data.AnnotatedItemStack;
import pigeon.notifiers.data.RareItemStack;
import pigeon.notifiers.data.SerializedItemStack;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemUtilsTest {
    @ParameterizedTest
    @ValueSource(longs = {2_147_483_647L, 2_147_483_648L, 5_000_000_000L, 2_149_631_130_647L})
    void preservesLargePricesThroughItemStacksAndWebhookJson(long price) {
        ItemManager itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(itemManager.getItemPrice(ItemID.RUBY)).thenReturn(price);
        when(itemManager.getItemComposition(ItemID.RUBY)).thenReturn(composition);
        when(composition.getMembersName()).thenReturn("Ruby");

        SerializedItemStack stack = ItemUtils.stackFromItem(itemManager, ItemID.RUBY, 3);
        AnnotatedItemStack annotated = AnnotatedItemStack.of(stack, Set.of(LootCriteria.VALUE));
        RareItemStack rare = RareItemStack.of(annotated, 0.001);

        assertEquals(price, ItemUtils.getPrice(itemManager, ItemID.RUBY));
        assertEquals(price, stack.getPriceEach());
        assertEquals(price * 3, stack.getTotalPrice());
        assertEquals(price * 6, ItemUtils.getTotalPrice(List.of(stack, rare)));
        Gson gson = new Gson();
        for (SerializedItemStack item : List.of(stack, annotated, rare)) {
            JsonObject payload = gson.toJsonTree(item.sanitized()).getAsJsonObject();
            assertEquals(price, payload.get("priceEach").getAsLong());
            SerializedItemStack restored = gson.fromJson(gson.toJson(item), SerializedItemStack.class);
            assertEquals(price, restored.getPriceEach());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void keepsStorePriceFallbackWhenMarketPriceIsUnavailable(long marketPrice) {
        ItemManager itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(itemManager.getItemPrice(ItemID.RUBY)).thenReturn(marketPrice);
        when(itemManager.getItemComposition(ItemID.RUBY)).thenReturn(composition);
        when(composition.getPrice()).thenReturn(1_000);

        assertEquals(1_000L, ItemUtils.getPrice(itemManager, ItemID.RUBY));
        assertEquals(3_000L, ItemUtils.stackFromItem(itemManager, ItemID.RUBY, 3).getTotalPrice());
    }
}
