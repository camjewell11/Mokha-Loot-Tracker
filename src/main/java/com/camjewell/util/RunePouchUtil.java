package com.camjewell.util;

import java.util.HashMap;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;

public final class RunePouchUtil {
    // Mapping of rune pouch varbit values (enum 982) to rune item IDs.
    // Varbits are 1-based (0 = empty), so index 0 is unused for direct lookup.
    private static final int[] RUNE_POUCH_ITEM_IDS = new int[] {
            0, // 0 unused / empty
            ItemID.AIR_RUNE, // 1
            ItemID.WATER_RUNE, // 2
            ItemID.EARTH_RUNE, // 3
            ItemID.FIRE_RUNE, // 4
            ItemID.MIND_RUNE, // 5
            ItemID.CHAOS_RUNE, // 6
            ItemID.DEATH_RUNE, // 7
            ItemID.BLOOD_RUNE, // 8
            ItemID.COSMIC_RUNE, // 9
            ItemID.NATURE_RUNE, // 10
            ItemID.LAW_RUNE, // 11
            ItemID.BODY_RUNE, // 12
            ItemID.SOUL_RUNE, // 13
            ItemID.ASTRAL_RUNE, // 14
            ItemID.MIST_RUNE, // 15
            ItemID.MUD_RUNE, // 16
            ItemID.DUST_RUNE, // 17
            ItemID.LAVA_RUNE, // 18
            ItemID.STEAM_RUNE, // 19
            ItemID.SMOKE_RUNE, // 20
            ItemID.WRATH_RUNE, // 21
            ItemID.SUNFIRE_RUNE, // 22
            ItemID.AETHER_RUNE // 23
    };

    private RunePouchUtil() {
    }

    public static Map<Integer, Integer> readRunePouch(Client client) {
        Map<Integer, Integer> map = new HashMap<>();
        int[] runeVarbits = new int[] { Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3,
                Varbits.RUNE_POUCH_RUNE4, Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6 };
        int[] amtVarbits = new int[] { Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2,
                Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4, Varbits.RUNE_POUCH_AMOUNT5,
                Varbits.RUNE_POUCH_AMOUNT6 };

        for (int i = 0; i < runeVarbits.length; i++) {
            int runeVar = client.getVarbitValue(runeVarbits[i]);
            int amt = client.getVarbitValue(amtVarbits[i]);
            if (runeVar <= 0 || amt <= 0) {
                continue; // 0 means empty slot
            }
            if (runeVar >= RUNE_POUCH_ITEM_IDS.length) {
                continue; // Unknown rune index
            }
            int itemId = RUNE_POUCH_ITEM_IDS[runeVar];
            if (itemId <= 0) {
                continue;
            }
            map.put(itemId, map.getOrDefault(itemId, 0) + amt);
        }
        return map;
    }
}
