package com.throwingdiscs.client;

import com.throwingdiscs.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ThrowingDiscsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.THROWN_DISC, ThrownDiscRenderer::new);
	}
}
