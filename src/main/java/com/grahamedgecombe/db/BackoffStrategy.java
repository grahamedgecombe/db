package com.grahamedgecombe.db;

@FunctionalInterface
public interface BackoffStrategy {
	public int getDelay(int attempt);
}
