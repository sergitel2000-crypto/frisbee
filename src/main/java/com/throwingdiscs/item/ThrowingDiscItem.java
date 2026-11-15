package com.throwingdiscs.item;

import com.throwingdiscs.entity.ModEntities;
import com.throwingdiscs.entity.ThrownDiscEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ThrowingDiscItem extends Item {
	private final float damage;
	private final double speed;
	private final float pitch;
	private final int enchantability;
	private final Item repairIngredient;

	public ThrowingDiscItem(Settings settings, float damage, double speed, float pitch, int enchantability, Item repairIngredient) {
		super(settings);
		this.damage = damage;
		this.speed = speed;
		this.pitch = pitch;
		this.enchantability = enchantability;
		this.repairIngredient = repairIngredient;
	}

	public float getDiscDamage() {
		return damage;
	}

	public double getDiscSpeed() {
		return speed;
	}

	public float getDiscPitch() {
		return pitch;
	}

	@Override
	public int getEnchantability() {
		return enchantability;
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return ingredient.isOf(repairIngredient);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		boolean creative = user.isCreative();

		// Like the trident: a disc on its very last point of durability won't leave your hand.
		if (!creative && stack.getMaxDamage() - stack.getDamage() <= 1) {
			return TypedActionResult.fail(stack);
		}

		if (!world.isClient) {
			ItemStack thrown = stack.copyWithCount(1);
			if (!creative) {
				thrown.damage(1, user, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			}
			int slot = hand == Hand.OFF_HAND ? 40 : user.getInventory().selectedSlot;
			ThrownDiscEntity disc = new ThrownDiscEntity(ModEntities.THROWN_DISC, world);
			disc.launch(user, thrown, slot, creative, hand == Hand.MAIN_HAND);
			world.spawnEntity(disc);
		}

		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}
}
