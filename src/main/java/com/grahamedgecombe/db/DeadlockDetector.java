package com.grahamedgecombe.db;

import java.sql.SQLException;

@FunctionalInterface
public interface DeadlockDetector {
	public boolean isDeadlocked(SQLException ex);
}
