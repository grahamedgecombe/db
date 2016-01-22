package com.grahamedgecombe.db;

import java.sql.Connection;

/**
 * Represents a database transaction that does not return a result.
 * @author Graham Edgecombe
 */
@FunctionalInterface
public interface VoidTransaction {
	/**
	 * Executes the transaction.
	 * @param connection The database {@link Connection}.
	 * @throws Exception if an error occurs.
	 */
	public void execute(Connection connection) throws Exception;
}
