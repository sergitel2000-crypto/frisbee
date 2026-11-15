package com.throwingdiscs.item;

import com.throwingdiscs.ThrowingDiscs;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	// name, durability, damage, speed (blocks/tick), sound pitch, enchantability, repair ingredient
	public static final ThrowingDiscItem COPPER = register("copper_throwing_disc",
		new ThrowingDiscItem(new Item.Settings().maxDamage(190), 4f, 1.3, 1.0f, 13, Items.COPPER_INGOT));
	public static final ThrowingDiscItem IRON = register("iron_throwing_disc",
		new ThrowingDiscItem(new Item.Settings().maxDamage(250), 5f, 1.3, 1.0f, 14, Items.IRON_INGOT));
	public static final ThrowingDiscItem GOLDEN = register("golden_throwing_disc",
		new ThrowingDiscItem(new Item.Settings().maxDamage(30), 3.5f, 1.55, 1.15f, 22, Items.GOLD_INGOT));
	public static final ThrowingDiscItem DIAMOND = register("diamond_throwing_disc",
		new ThrowingDiscItem(new Item.Settings().maxDamage(1560), 6f, 1.35, 1.1f, 10, Items.DIAMOND));
	public static final ThrowingDiscItem NETHERITE = register("netherite_throwing_disc",
		new ThrowingDiscItem(new Item.Settings().maxDamage(2030).fireproof(), 7f, 1.35, 0.85f, 15, Items.NETHERITE_INGOT));

	private ModItems() {}

	private static ThrowingDiscItem register(String name, ThrowingDiscItem item) {
		return Registry.register(Registries.ITEM, ThrowingDiscs.id(name), item);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(COPPER);
			entries.add(IRON);
			entries.add(GOLDEN);
			entries.add(DIAMOND);
			entries.add(NETHERITE);
		});
	}
}
