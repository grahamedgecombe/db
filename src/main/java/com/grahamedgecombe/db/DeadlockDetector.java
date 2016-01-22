package com.grahamedgecombe.db;

import java.sql.SQLException;

/**
 * Represents a function for determining if an {@link SQLException} represents
 * a deadlock or connection lost error.
 * @author Graham Edgecombe
 */
@FunctionalInterface
public interface DeadlockDetector {
	/**
	 * Checks if a given {@link SQLException} represents a deadlock or
	 * connection lost error.
	 * @param ex The {@link SQLException}.
	 * @return {@code true} if the exception represents a deadlock or
	 *         connection lost error, {@code} false otherwise.
	 */
	public boolean isDeadlocked(SQLException ex);
}
