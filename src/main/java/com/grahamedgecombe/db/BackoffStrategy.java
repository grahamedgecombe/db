package com.grahamedgecombe.db;

public interface BackoffStrategy {
	public int getDelay(int attempt);
}
