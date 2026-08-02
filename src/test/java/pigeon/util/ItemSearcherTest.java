package pigeon.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemSearcherTest {

    @Test
    void populateRetainsFirstUnnotedItemId() {
        ItemSearcher itemSearcher = new ItemSearcher();
        Map<Integer, String> namesById = new LinkedHashMap<>();
        namesById.put(100, "Example item");
        namesById.put(101, "Example item");
        namesById.put(102, "Noted item");

        itemSearcher.populate(namesById, Set.of(102));

        assertEquals(100, itemSearcher.findItemId("Example item"));
        assertNull(itemSearcher.findItemId("Noted item"));
        assertNull(itemSearcher.findItemId("Unknown item"));
    }
}
