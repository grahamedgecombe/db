package com.grahamedgecombe.db;

import java.util.Random;

import com.google.common.base.Preconditions;
import com.google.common.math.IntMath;

/**
 * A {@link BackoffStrategy} which generates a random delay between {@code 0}
 * and {@code 2^c - 1}, where {@code c} is the attempt number. {@code c} is
 * capped to {@code cMax}. The result is multiplied by the {@code scale} value
 * to obtain the delay in milliseconds.
 * @author Graham Edgecombe
 */
public final class BinaryExponentialBackoffStrategy implements BackoffStrategy {
	@SuppressWarnings("nullness:type.argument.type.incompatible")
	private static final ThreadLocal<Random> random = ThreadLocal.withInitial(Random::new);

	private final int cMax, scale;

	/**
	 * Creates a new {@link BinaryExponentialBackoffStrategy} object.
	 * @param cMax The value that {@code c} is capped to.
	 * @param scale The value that {@code 2^c - 1} is multiplied by to produce
	 *              a result in milliseconds.
	 */
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
