package com.grahamedgecombe.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ExecutionException;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DatabaseServiceTest {
	private @Nullable DatabaseService service;

	@Before
	public void setUp() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setURL("jdbc:hsqldb:mem:test");

		service = DatabaseService.builder(ds).setBackoffStrategy(attempt -> 0).build();
		service.startAsync().awaitRunning();
	}

	@After
	public void tearDown() {
		if (service != null) {
			service.stopAsync().awaitTerminated();
		}
	}

	@Test
	public void testSuccess() throws ExecutionException {
		if (service == null) {
			throw new IllegalStateException();
		}

		int result = service.execute(connection -> {
			try (PreparedStatement stmt = connection.prepareStatement("VALUES (42);")) {
				try (ResultSet rs = stmt.executeQuery()) {
					if (!rs.next()) {
						throw new IllegalStateException();
					}

					return rs.getInt(1);
				}
			}
		});

		assertEquals(42, result);
	}

	@Test(expected = ExecutionException.class)
	public void testFailure() throws ExecutionException {
		if (service == null) {
			throw new IllegalStateException();
		}

		service.<@Nullable Void>execute(connection -> {
			try (PreparedStatement stmt = connection.prepareStatement("INVALID SYNTAX")) {
				stmt.executeQuery();
				return null;
			}
		});
	}
}
