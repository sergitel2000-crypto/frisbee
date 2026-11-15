package com.throwingdiscs.entity;

import com.throwingdiscs.ThrowingDiscs;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<ThrownDiscEntity> THROWN_DISC = Registry.register(
		Registries.ENTITY_TYPE,
		ThrowingDiscs.id("thrown_disc"),
		EntityType.Builder.<ThrownDiscEntity>create(ThrownDiscEntity::new, SpawnGroup.MISC)
			.dimensions(0.5f, 0.5f)
			.maxTrackingRange(10)
			.trackingTickInterval(1)
			.build("thrown_disc")
	);

	private ModEntities() {}

	public static void register() {
		// Class loading registers the entity type.
	}
}
