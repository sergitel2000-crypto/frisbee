package com.throwingdiscs.client;

import com.throwingdiscs.entity.ModEntities;
import com.throwingdiscs.network.BlueFirePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ThrowingDiscsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.THROWN_DISC, ThrownDiscRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(BlueFirePayload.ID,
			(payload, context) -> BlueFire.mark(payload.entityId()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlueFire.clear());
	}
}
