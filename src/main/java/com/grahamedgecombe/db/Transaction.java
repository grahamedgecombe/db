package com.grahamedgecombe.db;

import java.sql.Connection;

@FunctionalInterface
public interface Transaction<T> {
	public T execute(Connection connection) throws Exception;
}
