package com.throwingdiscs.client.mixin;

import com.throwingdiscs.client.BlueFire;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the flames drawn on a burning entity for soul-fire (blue) ones when Flame Discharge lit it. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Unique
	private static final SpriteIdentifier THROWING_DISCS_SOUL_0 =
		new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.of("minecraft", "block/soul_fire_0"));
	@Unique
	private static final SpriteIdentifier THROWING_DISCS_SOUL_1 =
		new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.of("minecraft", "block/soul_fire_1"));

	@Unique
	private static Entity throwingDiscs$fireEntity;

	@Inject(method = "renderFire", at = @At("HEAD"))
	private void throwingDiscs$fireStart(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity, Quaternionf rotation, CallbackInfo ci) {
		throwingDiscs$fireEntity = entity;
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private void throwingDiscs$fireEnd(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity, Quaternionf rotation, CallbackInfo ci) {
		throwingDiscs$fireEntity = null;
	}

	@Redirect(method = "renderFire", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/util/SpriteIdentifier;getSprite()Lnet/minecraft/client/texture/Sprite;"))
	private Sprite throwingDiscs$fireSprite(SpriteIdentifier original) {
		Entity e = throwingDiscs$fireEntity;
		if (e != null && BlueFire.isBlue(e)) {
			boolean second = original.getTextureId().getPath().endsWith("fire_1");
			return (second ? THROWING_DISCS_SOUL_1 : THROWING_DISCS_SOUL_0).getSprite();
		}
		return original.getSprite();
	}
}
