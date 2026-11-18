package com.throwingdiscs.network;

import com.throwingdiscs.ThrowingDiscs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: "this entity is burning with Flame Discharge, draw its fire in blue". */
public record BlueFirePayload(int entityId) implements CustomPayload {
	public static final CustomPayload.Id<BlueFirePayload> ID = new CustomPayload.Id<>(ThrowingDiscs.id("blue_fire"));
	public static final PacketCodec<RegistryByteBuf, BlueFirePayload> CODEC =
		PacketCodec.tuple(PacketCodecs.VAR_INT, BlueFirePayload::entityId, BlueFirePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
