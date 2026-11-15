package com.throwingdiscs.client;

import com.throwingdiscs.entity.ThrownDiscEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Draws the disc's item sprite lying flat and spinning around the vertical axis. */
public class ThrownDiscRenderer extends EntityRenderer<ThrownDiscEntity> {
	private static final float SCALE = 1.1f;      // 16px sprite -> ~0.7 block wide disc
	private static final float SPIN_PER_TICK = 80f; // degrees

	private final net.minecraft.client.render.item.ItemRenderer itemRenderer;

	public ThrownDiscRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.itemRenderer = ctx.getItemRenderer();
		this.shadowRadius = 0.1f;
	}

	@Override
	public void render(ThrownDiscEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		ItemStack stack = entity.getStack();
		if (stack.isEmpty()) return;

		matrices.push();
		matrices.translate(0.0, 0.25, 0.0);
		matrices.scale(SCALE, SCALE, SCALE);
		// Lay the sprite flat (its normal pointing up), then spin it around that normal.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((entity.flightTicks + tickDelta) * SPIN_PER_TICK));
		itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV,
			matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();

		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(ThrownDiscEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
