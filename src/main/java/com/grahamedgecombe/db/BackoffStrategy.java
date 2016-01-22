package com.grahamedgecombe.db;

/**
 * Represents a function for calculating the delay between transaction
 * attempts.
 * @author Graham Edgecombe
 */
@FunctionalInterface
public interface BackoffStrategy {
	/**
	 * Calculates the delay between transaction attempts.
	 * @param attempt The zero-based attempt number.
	 * @throws IllegalArgumentException if {@code attempt} is negative.
	 * @return The delay in milliseconds.
	 */
	public int getDelay(int attempt);
}
