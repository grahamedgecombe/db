package com.grahamedgecombe.db;

import java.sql.Connection;

/**
 * Represents a database transaction that returns a result.
 * @param <T> The type of result returned by the transaction.
 * @author Graham Edgecombe
 */
@FunctionalInterface
public interface Transaction<T> {
	/**
	 * Executes the transaction.
	 * @param connection The database {@link Connection}.
	 * @return The result of the transaction
	 * @throws Exception if an error occurs.
	 */
	public T execute(Connection connection) throws Exception;
}
