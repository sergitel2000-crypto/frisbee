package com.throwingdiscs.entity;

import com.throwingdiscs.item.ThrowingDiscItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The flying disc. Port of the Bedrock script: it flies straight (no gravity), ricochets off hard
 * blocks, pierces every mob in its path, then curves back to the thrower like a boomerang.
 * Each ricochet buys it more flight time. Soft blocks (wool, leaves, hay...) kill its momentum so
 * it heads home. All movement is simulated on the server; clients just render the synced position.
 */
public class ThrownDiscEntity extends Entity {
	private static final TrackedData<ItemStack> STACK =
		DataTracker.registerData(ThrownDiscEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

	// ---- tuning (same numbers as the Bedrock addon) ----
	private static final int OUT_TICKS = 12;            // straight-line flight before turning home (~16 blocks)
	private static final int BOUNCE_BONUS_TICKS = 5;    // every ricochet extends the outward flight
	private static final int SLIME_BONUS_TICKS = 10;    // slime blocks fling it further and don't count as a bounce
	private static final int MAX_BOUNCES = 6;
	private static final double RETURN_START_SPEED = 0.45;
	private static final double RETURN_PULL = 0.3;
	private static final double RETURN_DRAG = 0.88;
	private static final double RETURN_MAX_SPEED = 1.7;
	private static final int RETURN_STALL_TICKS = 20;   // no progress home for this long = stuck, it drops
	private static final double CATCH_RADIUS = 1.5;
	private static final int MIN_CATCH_AGE = 5;
	private static final int MAX_AGE = 400;             // 20 s in the air and it drops
	private static final double MAX_OWNER_DISTANCE = 96;
	private static final int MAX_HITS_PER_TARGET = 3;
	private static final double DISC_RADIUS = 0.35;
	private static final double SKIN = 0.04;            // gap kept between the disc and a surface it touches
	private static final double CENTER_OFFSET = 0.25;   // entity origin -> centre of the disc (hitbox is 0.5 tall)
	private static final double BLOCK_PROBE_RING = 8;

	/** Ticks since spawn; used by the renderer for the spin (client) and by the flight logic (server). */
	public int flightTicks;

	private UUID ownerUuid;
	private int slot;              // inventory slot it came from (40 = off-hand)
	private boolean creative;
	private Vec3d dir = new Vec3d(0, 0, 1);
	private int outLeft = OUT_TICKS;
	private int bounces;
	private boolean returning;
	private Vec3d vel = Vec3d.ZERO;
	private double backSpeed = RETURN_START_SPEED;
	private double backBest = Double.MAX_VALUE;
	private int backStall;
	private int lastClink = -100;
	private final Set<UUID> legHits = new HashSet<>();
	private final Map<UUID, Integer> hitCounts = new HashMap<>();
	private int sharpness, smite, bane, knockback, fireAspect;

	public ThrownDiscEntity(EntityType<? extends ThrownDiscEntity> type, World world) {
		super(type, world);
	}

	// ---------------------------------------------------------------- setup

	/** Called server-side right after creating the entity, before spawning it. */
	public void launch(PlayerEntity owner, ItemStack stack, int slot, boolean creative, boolean mainHand) {
		this.ownerUuid = owner.getUuid();
		this.slot = slot;
		this.creative = creative;
		setStack(stack);
		refreshEnchantments();

		Vec3d look = owner.getRotationVec(1.0f).normalize();
		this.dir = look;
		Vec3d right = new Vec3d(-look.z, 0, look.x);
		right = right.lengthSquared() > 1.0E-6 ? right.normalize() : new Vec3d(1, 0, 0);

		Vec3d eye = owner.getEyePos().add(0, -0.2, 0);
		Vec3d release = look.multiply(0.4).add(right.multiply(mainHand ? 0.25 : -0.25));
		// Release point a little in front, on the throwing hand's side - unless a wall is right there.
		Vec3d center = clear((ServerWorld) getWorld(), eye, eye.add(release)) ? eye.add(release) : eye;
		setPosition(center.x, center.y - CENTER_OFFSET, center.z);

		ThrowingDiscItem item = (ThrowingDiscItem) stack.getItem();
		play((ServerWorld) getWorld(), SoundEvents.ITEM_TRIDENT_THROW.value(), center, 0.8f, 1.4f * item.getDiscPitch());
	}

	public ItemStack getStack() {
		return dataTracker.get(STACK);
	}

	public void setStack(ItemStack stack) {
		dataTracker.set(STACK, stack);
	}

	private void refreshEnchantments() {
		ItemStack s = getStack();
		var lookup = getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
		sharpness = EnchantmentHelper.getLevel(lookup.getOrThrow(Enchantments.SHARPNESS), s);
		smite = EnchantmentHelper.getLevel(lookup.getOrThrow(Enchantments.SMITE), s);
		bane = EnchantmentHelper.getLevel(lookup.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), s);
		knockback = EnchantmentHelper.getLevel(lookup.getOrThrow(Enchantments.KNOCKBACK), s);
		fireAspect = EnchantmentHelper.getLevel(lookup.getOrThrow(Enchantments.FIRE_ASPECT), s);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(STACK, ItemStack.EMPTY);
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Disc", NbtElement.COMPOUND_TYPE)) {
			ItemStack.fromNbt(getRegistryManager(), nbt.getCompound("Disc")).ifPresent(this::setStack);
		}
		if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
		slot = nbt.getInt("Slot");
		creative = nbt.getBoolean("Creative");
		// A disc that was mid-flight when the world saved simply heads home.
		returning = true;
		flightTicks = MIN_CATCH_AGE;
		vel = Vec3d.ZERO;
		if (!getStack().isEmpty()) refreshEnchantments();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (!getStack().isEmpty()) nbt.put("Disc", getStack().encode(getRegistryManager()));
		if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
		nbt.putInt("Slot", slot);
		nbt.putBoolean("Creative", creative);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		flightTicks++;
		if (getWorld().isClient) return;

		ServerWorld world = (ServerWorld) getWorld();
		ItemStack stack = getStack();
		if (!(stack.getItem() instanceof ThrowingDiscItem item)) {
			discard();
			return;
		}
		if (flightTicks > MAX_AGE) {
			dropAsItem(world);
			return;
		}

		Vec3d center = center();
		PlayerEntity owner = getOwnerPlayer(world, center);

		// Catchable any time after leaving the hand, so a ricochet straight back is a catch too.
		if (owner != null && flightTicks >= MIN_CATCH_AGE && center.distanceTo(catchPoint(owner)) <= CATCH_RADIUS) {
			catchDisc(world, owner);
			return;
		}

		Vec3d end;
		if (!returning) {
			double speed = item.getDiscSpeed() * (inWater(world, center) ? 0.6 : 1.0);
			if (insideSolid(world, center)) {
				end = center;
				startReturn(world, center, owner != null, item);
			} else {
				Contact contact = sweepThroughPots(world, center, dir, speed);
				if (contact == null) {
					end = center.add(dir.multiply(speed));
				} else {
					// Stop on the surface this tick - the bounce is seen touching the block.
					end = center.add(dir.multiply(contact.travel)).add(contact.normal.multiply(SKIN));
					Kind kind = kindOf(contact.state);
					legHits.clear();
					dir = reflect(dir, contact.normal).normalize();
					switch (kind) {
						case SOFT -> {
							play(world, SoundEvents.BLOCK_WOOL_STEP, contact.point, 0.9f, 0.8f);
							startReturn(world, end, owner != null, item);
						}
						case SLIME -> {
							outLeft += SLIME_BONUS_TICKS;
							play(world, SoundEvents.BLOCK_SLIME_BLOCK_HIT, contact.point, 1.0f, 1.0f);
							spark(world, contact.point);
						}
						default -> {
							bounces++;
							outLeft += BOUNCE_BONUS_TICKS;
							play(world, SoundEvents.ITEM_TRIDENT_HIT_GROUND, contact.point, 0.9f, 1.55f * item.getDiscPitch());
							spark(world, contact.point);
							if (kind == Kind.BELL) play(world, SoundEvents.BLOCK_BELL_USE, contact.point, 1.0f, 1.0f);
							if (bounces > MAX_BOUNCES) startReturn(world, end, owner != null, item);
						}
					}
				}
			}
			outLeft--;
			if (!returning && outLeft <= 0) startReturn(world, end, owner != null, item);
		} else {
			if (owner == null) { // thrower gone (left, died, changed dimension)
				dropAsItem(world);
				return;
			}
			Vec3d target = catchPoint(owner);
			Vec3d toTarget = target.subtract(center);
			double d = toTarget.length();
			// Close enough and nothing in between: caught.
			if (d <= backSpeed + 0.6 && clear(world, center, target)) {
				catchDisc(world, owner);
				return;
			}
			// Progress check: if it hasn't got any closer for a while, it's stuck behind something.
			if (d < backBest - 0.2) {
				backBest = d;
				backStall = 0;
			} else if (++backStall > RETURN_STALL_TICKS) {
				dropAsItem(world);
				return;
			}
			if (insideSolid(world, center)) {
				dropAsItem(world);
				return;
			}

			// Boomerang return: momentum decays while a pull toward the thrower builds,
			// so it slows, stops and flies straight back (curving only if you've moved).
			double maxSpeed = RETURN_MAX_SPEED * (item.getDiscSpeed() / 1.3);
			vel = vel.multiply(RETURN_DRAG).add(toTarget.normalize().multiply(RETURN_PULL));
			double speed = vel.length();
			if (speed > maxSpeed) vel = vel.multiply(maxSpeed / speed);
			backSpeed = Math.min(speed, maxSpeed);
			dir = safeNormalize(vel, dir);
			double step = backSpeed;

			Contact contact = sweepThroughPots(world, center, dir, step);
			if (contact == null) {
				end = center.add(dir.multiply(step));
			} else {
				end = center.add(dir.multiply(contact.travel)).add(contact.normal.multiply(SKIN));
				double into = dir.dotProduct(contact.normal);
				if (into < -0.55 && backSpeed > 0.4 && flightTicks - lastClink > 4) {
					lastClink = flightTicks;
					SoundEvent s = kindOf(contact.state) == Kind.SOFT ? SoundEvents.BLOCK_WOOL_STEP : SoundEvents.ITEM_TRIDENT_HIT_GROUND;
					play(world, s, contact.point, 0.5f, 1.55f * item.getDiscPitch());
				}
				// Slide along the surface toward home (skims over a ledge instead of tunnelling through it).
				double vin = vel.dotProduct(contact.normal);
				vel = vel.subtract(contact.normal.multiply(vin)).multiply(0.85);
				dir = safeNormalize(vel, dir);
			}
		}

		hitAlong(world, center, end, owner, item);
		if (isRemoved()) return;
		// Exact per-tick placement, so clients never see it inside a wall.
		setPosition(end.x, end.y - CENTER_OFFSET, end.z);
	}

	private Vec3d center() {
		return getPos().add(0, CENTER_OFFSET, 0);
	}

	private static Vec3d catchPoint(PlayerEntity owner) {
		return owner.getPos().add(0, 0.95, 0);
	}

	private PlayerEntity getOwnerPlayer(ServerWorld world, Vec3d at) {
		if (ownerUuid == null) return null;
		PlayerEntity p = world.getPlayerByUuid(ownerUuid);
		if (p == null || !p.isAlive() || p.isSpectator()) return null;
		if (p.getWorld() != world) return null;
		if (p.getPos().distanceTo(at) > MAX_OWNER_DISTANCE) return null;
		return p;
	}

	private void startReturn(ServerWorld world, Vec3d at, boolean ownerPresent, ThrowingDiscItem item) {
		if (returning) return;
		returning = true;
		backSpeed = RETURN_START_SPEED;
		// Keeps its momentum, slows, then comes straight back - no sideways swing.
		vel = dir.multiply(item.getDiscSpeed() * 0.8);
		legHits.clear();
		if (ownerPresent) play(world, SoundEvents.ITEM_TRIDENT_RETURN, at, 0.6f, 1.35f * item.getDiscPitch());
	}

	// ---------------------------------------------------------------- catch / drop

	private void catchDisc(ServerWorld world, PlayerEntity owner) {
		ItemStack stack = getStack().copy();
		discard();
		if (!creative && !stack.isEmpty()) giveBack(owner, stack);
		play(world, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), owner.getPos().add(0, 1, 0), 0.8f, 1.45f);
	}

	private void giveBack(PlayerEntity player, ItemStack stack) {
		var inv = player.getInventory();
		if (slot >= 0 && slot < inv.size() && inv.getStack(slot).isEmpty()) {
			inv.setStack(slot, stack);
			return;
		}
		if (!inv.insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	private void dropAsItem(ServerWorld world) {
		Vec3d at = center();
		ItemStack stack = getStack().copy();
		discard();
		if (!creative && !stack.isEmpty()) {
			ItemScatterer.spawn(world, at.x, at.y, at.z, stack);
			float pitch = stack.getItem() instanceof ThrowingDiscItem i ? i.getDiscPitch() : 1f;
			play(world, SoundEvents.ITEM_TRIDENT_HIT_GROUND, at, 0.6f, 1.2f * pitch);
		}
	}

	// ---------------------------------------------------------------- hitting mobs

	private void hitAlong(ServerWorld world, Vec3d a, Vec3d b, PlayerEntity owner, ThrowingDiscItem item) {
		Vec3d seg = b.subtract(a);
		Vec3d dirN = seg.lengthSquared() > 1.0E-8 ? seg.normalize() : dir;
		Box search = new Box(a, b).expand(DISC_RADIUS + 2.0);
		for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, search, e -> e.isAlive())) {
			UUID id = t.getUuid();
			if (id.equals(ownerUuid) || legHits.contains(id) || hitCounts.getOrDefault(id, 0) >= MAX_HITS_PER_TARGET) continue;
			if (!canHit(world, t, owner)) continue;
			Box body = t.getBoundingBox().expand(DISC_RADIUS);
			if (!body.contains(a) && body.raycast(a, b).isEmpty()) continue;
			legHits.add(id);
			hitCounts.merge(id, 1, Integer::sum);
			strike(world, t, owner, item, dirN);
		}
	}

	private boolean canHit(ServerWorld world, LivingEntity target, PlayerEntity owner) {
		if (target instanceof ArmorStandEntity) return false;
		if (target instanceof PlayerEntity p) {
			if (!world.getServer().isPvpEnabled()) return false;
			if (p.isCreative() || p.isSpectator()) return false;
		}
		// Leave tamed animals alone - your dog chasing your target shouldn't eat a disc.
		if (target instanceof TameableEntity tame && tame.isTamed()) return false;
		return owner == null || owner.getVehicle() != target;
	}

	private void strike(ServerWorld world, LivingEntity target, PlayerEntity owner, ThrowingDiscItem item, Vec3d dirN) {
		float damage = item.getDiscDamage() + 1.25f * sharpness;
		if (smite > 0 && target.getType().isIn(EntityTypeTags.UNDEAD)) damage += 2.5f * smite;
		if (bane > 0 && target.getType().isIn(EntityTypeTags.ARTHROPOD)) damage += 2.5f * bane;

		DamageSource source = world.getDamageSources().thrown(this, owner);
		if (!target.damage(source, damage)) return;

		double kb = 0.35 + 0.5 * knockback;
		target.takeKnockback(kb, -dirN.x, -dirN.z);
		if (fireAspect > 0) target.setOnFireFor(4 * fireAspect);
		play(world, SoundEvents.ITEM_TRIDENT_HIT, target.getPos().add(0, target.getHeight() * 0.5, 0), 0.8f, 1.3f * item.getDiscPitch());
	}

	// ---------------------------------------------------------------- block collision

	private enum Kind { HARD, SOFT, SLIME, POT, BELL }

	private static Kind kindOf(BlockState s) {
		if (s.isOf(Blocks.SLIME_BLOCK)) return Kind.SLIME;
		if (s.isOf(Blocks.DECORATED_POT)) return Kind.POT;
		if (s.isOf(Blocks.BELL)) return Kind.BELL;
		if (s.isIn(BlockTags.WOOL) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.BEDS)
			|| s.isOf(Blocks.HAY_BLOCK) || s.isOf(Blocks.SPONGE) || s.isOf(Blocks.WET_SPONGE)
			|| s.isOf(Blocks.MOSS_BLOCK) || s.isOf(Blocks.SNOW) || s.isOf(Blocks.POWDER_SNOW)
			|| s.isOf(Blocks.HONEY_BLOCK) || s.isOf(Blocks.MUSHROOM_STEM)
			|| s.isOf(Blocks.RED_MUSHROOM_BLOCK) || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK)
			|| s.isOf(Blocks.DRIED_KELP_BLOCK) || s.isOf(Blocks.AZALEA) || s.isOf(Blocks.FLOWERING_AZALEA)) {
			return Kind.SOFT;
		}
		return Kind.HARD;
	}

	private record Contact(double travel, Vec3d normal, BlockPos pos, BlockState state, Vec3d point) {}

	/**
	 * Sweep the disc (a circle of DISC_RADIUS perpendicular to its travel direction) from c along
	 * the unit vector d for up to `distance` blocks: one centre ray plus a ring of rim rays.
	 */
	private Contact sweep(ServerWorld world, Vec3d c, Vec3d d, double distance) {
		Vec3d ref = Math.abs(d.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
		Vec3d u = d.crossProduct(ref).normalize();
		Vec3d v = d.crossProduct(u).normalize();
		Contact best = null;
		for (int i = -1; i < BLOCK_PROBE_RING; i++) {
			Vec3d origin;
			double lead;
			if (i < 0) {
				origin = c;
				lead = DISC_RADIUS;
			} else {
				double ang = 2 * Math.PI * i / BLOCK_PROBE_RING;
				origin = c.add(u.multiply(Math.cos(ang) * DISC_RADIUS)).add(v.multiply(Math.sin(ang) * DISC_RADIUS));
				lead = 0;
			}
			Vec3d to = origin.add(d.multiply(distance + lead + SKIN));
			BlockHitResult hit = world.raycast(new RaycastContext(origin, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
			if (hit.getType() != HitResult.Type.BLOCK) continue;
			Vec3d normal = Vec3d.of(hit.getSide().getVector());
			if (d.dotProduct(normal) > -0.01) continue; // only faces it is actually moving into
			double along = hit.getPos().subtract(origin).dotProduct(d);
			double travel = MathHelper.clamp(along - lead - SKIN, 0, distance);
			if (best == null || travel < best.travel) {
				BlockPos bp = hit.getBlockPos();
				best = new Contact(travel, normal, bp, world.getBlockState(bp), hit.getPos());
			}
		}
		return best;
	}

	/** Sweep, smashing any decorated pots on the way (they don't stop the disc). */
	private Contact sweepThroughPots(ServerWorld world, Vec3d c, Vec3d d, double distance) {
		for (int i = 0; i < 4; i++) {
			Contact contact = sweep(world, c, d, distance);
			if (contact == null || kindOf(contact.state) != Kind.POT) return contact;
			world.breakBlock(contact.pos, true, this);
		}
		return sweep(world, c, d, distance);
	}

	private boolean clear(ServerWorld world, Vec3d a, Vec3d b) {
		return world.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
			.getType() == HitResult.Type.MISS;
	}

	private static boolean insideSolid(ServerWorld world, Vec3d p) {
		BlockPos bp = BlockPos.ofFloored(p);
		VoxelShape shape = world.getBlockState(bp).getCollisionShape(world, bp);
		if (shape.isEmpty()) return false;
		for (Box box : shape.getBoundingBoxes()) {
			if (box.offset(bp).contains(p)) return true;
		}
		return false;
	}

	private static boolean inWater(ServerWorld world, Vec3d p) {
		return world.getFluidState(BlockPos.ofFloored(p)).getFluid() == Fluids.WATER
			|| world.getFluidState(BlockPos.ofFloored(p)).getFluid() == Fluids.FLOWING_WATER;
	}

	// ---------------------------------------------------------------- helpers

	private static Vec3d reflect(Vec3d d, Vec3d n) {
		return d.subtract(n.multiply(2 * d.dotProduct(n)));
	}

	private static Vec3d safeNormalize(Vec3d v, Vec3d fallback) {
		return v.lengthSquared() > 1.0E-9 ? v.normalize() : fallback;
	}

	private void play(ServerWorld world, SoundEvent sound, Vec3d at, float volume, float pitch) {
		world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.PLAYERS, volume, pitch * (0.92f + random.nextFloat() * 0.16f));
	}

	private static void spark(ServerWorld world, Vec3d at) {
		world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.15);
	}
}
