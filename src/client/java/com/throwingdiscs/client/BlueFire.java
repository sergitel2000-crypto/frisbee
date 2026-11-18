package com.throwingdiscs.client;

import net.minecraft.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side record of which entities are burning with blue fire. */
public final class BlueFire {
	private static final Map<Integer, Long> MARKED = new ConcurrentHashMap<>();
	private static final long GRACE_MS = 1500; // the "on fire" flag can reach the client a moment after this marker

	private BlueFire() {}

	public static void mark(int entityId) {
		MARKED.put(entityId, System.currentTimeMillis());
	}

	public static void clear() {
		MARKED.clear();
	}

	public static boolean isBlue(Entity entity) {
		Long since = MARKED.get(entity.getId());
		if (since == null) return false;
		if (entity.isOnFire()) return true;
		if (System.currentTimeMillis() - since > GRACE_MS) MARKED.remove(entity.getId());
		return false;
	}
}
