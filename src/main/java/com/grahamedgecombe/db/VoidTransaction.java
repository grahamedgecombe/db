package com.grahamedgecombe.db;

import java.sql.Connection;

@FunctionalInterface
public interface VoidTransaction {
	public void execute(Connection connection) throws Exception;
}
