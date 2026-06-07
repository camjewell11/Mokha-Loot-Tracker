package com.camjewell;

enum TrackedWeapon {

    // ── Blowpipe variants (JSON array format: [{itemId, quantity}, ...]) ──────────
    // itemIds covers both the loaded (filled) and empty forms so detectWeaponsOnPlayer
    // finds the weapon regardless of whether it currently has darts/scales in it.
    // chargeRecipe is null — blowpipe dart/scale items are tracked directly from the JSON delta.

    TOXIC_BLOWPIPE("Toxic Blowpipe",
            new int[]{ 12924, 12926 },
            "toxic_blowpipe_storage",
            ConfigFormat.BLOWPIPE_JSON,
            "blowpipe",
            null),

    CAMPHOR_BLOWPIPE("Camphor Blowpipe",
            new int[]{ 31575, 31577 },
            "camphor_blowpipe_storage",
            ConfigFormat.BLOWPIPE_JSON,
            "camphor blowpipe",
            null),

    IRONWOOD_BLOWPIPE("Ironwood Blowpipe",
            new int[]{ 31579, 31581 },
            "ironwood_blowpipe_storage",
            ConfigFormat.BLOWPIPE_JSON,
            "ironwood blowpipe",
            null),

    ROSEWOOD_BLOWPIPE("Rosewood Blowpipe",
            new int[]{ 31583, 31585 },
            "rosewood_blowpipe_storage",
            ConfigFormat.BLOWPIPE_JSON,
            "rosewood blowpipe",
            null),

    // Cosmetic variant of Toxic Blowpipe (ornament kit applied).
    BLAZING_BLOWPIPE("Blazing Blowpipe",
            new int[]{ 28687, 28688 },
            "blazing_blowpipe_storage",
            ConfigFormat.BLOWPIPE_JSON,
            "blazing blowpipe",
            null),

    // ── Powered staves / single-charge weapons (plain integer format) ─────────────
    // tictac7x config key is the plain item name with no "_charges" suffix.
    // itemIds[0] is the canonical ID used as the charge-map key; remaining IDs are
    // alternate forms detected in inventory/equipment only.
    // chargeRecipe describes the items consumed per charge for cost calculation.
    // Ingredient item IDs defined as constants in ChargeIngredient below.

    VENATOR_BOW("Venator Bow",
            new int[]{ 27610, 27612 },
            "venator_bow",
            ConfigFormat.INTEGER,
            "venator",
            new ChargeIngredient[]{
                new ChargeIngredient(27616, 1)                  // ancient essence
            }),

    TRIDENT_SEAS("Trident of the Seas",
            new int[]{ 11907, 11905, 11908, 22288, 22290 },
            "trident_of_the_seas",
            ConfigFormat.INTEGER,
            "trident of the seas",
            new ChargeIngredient[]{
                new ChargeIngredient(560, 1),                   // death rune
                new ChargeIngredient(562, 1),                   // chaos rune
                new ChargeIngredient(554, 5)                    // fire rune
            }),

    TRIDENT_SWAMP("Trident of the Swamp",
            new int[]{ 12899, 12900, 22292, 22294 },
            "trident_of_the_swamp",
            ConfigFormat.INTEGER,
            "trident of the swamp",
            new ChargeIngredient[]{
                new ChargeIngredient(560, 1),                   // death rune
                new ChargeIngredient(562, 1),                   // chaos rune
                new ChargeIngredient(554, 5),                   // fire rune
                new ChargeIngredient(12934, 1)                  // zulrah's scale (SNAKEBOSS_SCALE)
            }),

    SANGUINESTI_STAFF("Sanguinesti Staff",
            new int[]{ 22323, 22481 },
            "sanguinesti_staff",
            ConfigFormat.INTEGER,
            "sanguinesti",
            new ChargeIngredient[]{
                new ChargeIngredient(565, 3)                    // blood rune
            }),

    TUMEKENS_SHADOW("Tumeken's Shadow",
            new int[]{ 27275, 27277 },
            "tumekens_shadow",
            ConfigFormat.INTEGER,
            "tumeken",
            new ChargeIngredient[]{
                new ChargeIngredient(566, 2),                   // soul rune
                new ChargeIngredient(562, 5)                    // chaos rune
            }),

    // Can be recharged with 2x death rune + 1x chaos rune OR 1x demon tear.
    // Defaulting to demon tear since we cannot determine which was used.
    EYE_OF_AYAK("Eye of Ayak",
            new int[]{ 31113, 31115 },
            "eye_of_ayak",
            ConfigFormat.INTEGER,
            "eye of ayak",
            new ChargeIngredient[]{
                new ChargeIngredient(31111, 1)                  // demon tear (ItemID.DEMON_TEAR)
            }),

    // 100 charges per 1 vial of blood and 200 blood runes.
    SCYTHE_OF_VITUR("Scythe of Vitur",
            new int[]{ 22325, 22486 },
            "scythe_of_vitur",
            ConfigFormat.INTEGER,
            "scythe",
            new ChargeIngredient[]{
                new ChargeIngredient(565, 2),                   // blood rune (2 per charge)
                new ChargeIngredient(22446, 1, 100)             // vial of blood (1 per 100 charges)
            }),

    // Crystal shards are untradeable — tracked for quantity only, no GP cost.
    BLADE_OF_SAELDOR("Blade of Saeldor",
            new int[]{ 23995, 23997 },
            "blade_of_saeldor",
            ConfigFormat.INTEGER,
            "blade of saeldor",
            new ChargeIngredient[]{
                new ChargeIngredient(23962, 1, 100, true)       // crystal shard (priceless)
            }),

    BOW_OF_FAERDHINEN("Bow of Faerdhinen",
            new int[]{ 25865, 25862 },
            "bow_of_faerdhinen",
            ConfigFormat.INTEGER,
            "faerdhinen",
            new ChargeIngredient[]{
                new ChargeIngredient(23962, 1, 100, true)       // crystal shard (priceless)
            }),

    CRYSTAL_BOW("Crystal Bow",
            new int[]{ 23983, 23985, 24123 },
            "crystal_bow",
            ConfigFormat.INTEGER,
            "crystal bow",
            new ChargeIngredient[]{
                new ChargeIngredient(23962, 1, 100, true)       // crystal shard (priceless)
            }),

    CRYSTAL_HALBERD("Crystal Halberd",
            new int[]{ 23987, 23989, 24125 },
            "crystal_halberd",
            ConfigFormat.INTEGER,
            "crystal halberd",
            new ChargeIngredient[]{
                new ChargeIngredient(23962, 1, 100, true)       // crystal shard (priceless)
            }),

    // Includes cyan and red recolour variants (charged + uncharged each).
    SERPENTINE_HELM("Serpentine Helm",
            new int[]{ 12931, 12929, 13197, 13196, 13199, 13198 },
            "serpentine_helm",
            ConfigFormat.INTEGER,
            "serpentine",
            new ChargeIngredient[]{
                new ChargeIngredient(12934, 1)                  // zulrah's scale
            });

    // ─────────────────────────────────────────────────────────────────────────────

    enum ConfigFormat { BLOWPIPE_JSON, INTEGER }

    /**
     * One ingredient in a per-charge recipe.
     * Cost per charge = (numerator / denominator) × GE price of itemId.
     * priceless items are counted in tooltips but contribute 0 GP to cost.
     */
    static final class ChargeIngredient {
        // Item IDs referenced in charge recipes (from RuneLite gameval constants).
        // Declared here so they're co-located with the recipes that use them.
        static final int FIRE_RUNE       = 554;   // ItemID.FIRUNE
        static final int DEATH_RUNE      = 560;   // ItemID.DEATHRUNE
        static final int CHAOS_RUNE      = 562;   // ItemID.CHAOSRUNE
        static final int BLOOD_RUNE      = 565;   // ItemID.BLOODRUNE
        static final int SOUL_RUNE       = 566;   // ItemID.SOULRUNE
        static final int ZULRAH_SCALE    = 12934; // ItemID.SNAKEBOSS_SCALE
        static final int VIAL_OF_BLOOD   = 22446; // ItemID.VIAL_BLOOD
        static final int CRYSTAL_SHARD   = 23962; // ItemID.PRIF_CRYSTAL_SHARD
        static final int ANCIENT_ESSENCE = 27616; // ItemID.ANCIENT_ESSENCE

        final int itemId;
        final int numerator;    // items consumed per `denominator` charges
        final int denominator;  // charge batch size (1 = per charge, 100 = per 100 charges)
        final boolean priceless;

        ChargeIngredient(int itemId, int numerator) {
            this(itemId, numerator, 1, false);
        }

        ChargeIngredient(int itemId, int numerator, int denominator) {
            this(itemId, numerator, denominator, false);
        }

        ChargeIngredient(int itemId, int numerator, int denominator, boolean priceless) {
            this.itemId = itemId;
            this.numerator = numerator;
            this.denominator = denominator;
            this.priceless = priceless;
        }

        /** Total quantity of this ingredient for {@code charges} charges consumed. */
        double totalQuantity(int charges) {
            return (double) charges * numerator / denominator;
        }
    }

    final String displayName;
    final int[] itemIds;
    final String tictacConfigKey;
    final ConfigFormat configFormat;
    /** Lowercase substring expected in a "check charges" chat message for this weapon. */
    final String chatKeyword;
    /**
     * Charge recipe for cost calculation. Null for BLOWPIPE_JSON weapons (items tracked
     * directly from the JSON storage delta). Empty array means recipe is unknown.
     */
    final ChargeIngredient[] chargeRecipe;

    TrackedWeapon(String displayName, int[] itemIds, String tictacConfigKey,
                  ConfigFormat configFormat, String chatKeyword, ChargeIngredient[] chargeRecipe) {
        this.displayName = displayName;
        this.itemIds = itemIds;
        this.tictacConfigKey = tictacConfigKey;
        this.configFormat = configFormat;
        this.chatKeyword = chatKeyword;
        this.chargeRecipe = chargeRecipe;
    }

    /** Returns the TrackedWeapon whose tictac7x config key matches, or null. */
    static TrackedWeapon fromConfigKey(String key) {
        if (key == null) return null;
        for (TrackedWeapon w : values()) {
            if (w.tictacConfigKey.equals(key)) return w;
        }
        return null;
    }

    /** Returns the INTEGER-format TrackedWeapon whose canonical item ID matches, or null. */
    static TrackedWeapon fromCanonicalItemId(int itemId) {
        if (itemId <= 0) return null;
        for (TrackedWeapon w : values()) {
            if (w.configFormat == ConfigFormat.INTEGER && w.itemIds[0] == itemId) return w;
        }
        return null;
    }

    /**
     * Returns true if itemId matches any item ID declared for an INTEGER-format weapon.
     * Used to decide whether to apply 0 GP cost when committing charge consumption.
     */
    static boolean isConfigChargeItemId(int itemId) {
        if (itemId <= 0) return false;
        for (TrackedWeapon w : values()) {
            if (w.configFormat != ConfigFormat.INTEGER) continue;
            for (int id : w.itemIds) {
                if (id == itemId) return true;
            }
        }
        return false;
    }
}
