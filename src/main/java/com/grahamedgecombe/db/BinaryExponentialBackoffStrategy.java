package com.grahamedgecombe.db;

import java.util.Random;

import com.google.common.base.Preconditions;
import com.google.common.math.IntMath;

public final class BinaryExponentialBackoffStrategy implements BackoffStrategy {
	@SuppressWarnings("nullness:type.argument.type.incompatible")
	private static final ThreadLocal<Random> random = ThreadLocal.withInitial(Random::new);

	private final int cMax, scale;

	public BinaryExponentialBackoffStrategy(int cMax, int scale) {
		Preconditions.checkArgument(cMax > 0, "cMax must be positive");
		Preconditions.checkArgument(scale > 0, "scale must be positive");
		this.cMax = cMax;
		this.scale = scale;
	}

	@Override
	public int getDelay(int attempt) {
		Preconditions.checkArgument(attempt >= 0, "attempt must be zero or positive");
		return random.get().nextInt(IntMath.pow(2, Math.min(attempt + 1, cMax)) + 1) * scale;
	}
}
