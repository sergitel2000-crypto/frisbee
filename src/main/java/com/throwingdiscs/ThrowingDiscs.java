package com.throwingdiscs;

import com.throwingdiscs.entity.ModEntities;
import com.throwingdiscs.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;

public class ThrowingDiscs implements ModInitializer {
	public static final String MOD_ID = "throwing_discs";

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();

		// The Bedrock addon injected a diamond disc into the Trial Chambers rare reward pools.
		// Weights can't be edited in place, so it is added as an extra low-chance roll instead.
		LootTableEvents.MODIFY.register((key, tableBuilder, source) -> {
			Identifier id = key.getValue();
			float chance;
			if (id.equals(Identifier.of("minecraft", "chests/trial_chambers/reward_rare"))) chance = 0.04f;
			else if (id.equals(Identifier.of("minecraft", "chests/trial_chambers/reward_ominous_rare"))) chance = 0.09f;
			else return;
			tableBuilder.pool(LootPool.builder()
				.rolls(ConstantLootNumberProvider.create(1))
				.conditionally(RandomChanceLootCondition.builder(chance))
				.with(ItemEntry.builder(ModItems.DIAMOND)));
		});
	}
}
