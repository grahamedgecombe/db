package com.grahamedgecombe.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TransactionExecutor implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TransactionExecutor.class);

	private final DataSource dataSource;
	private final BackoffStrategy backoffStrategy;
	private final DeadlockDetector deadlockDetector;
	private final int maxAttempts;
	private final BlockingQueue<TransactionJob<?>> jobs;
	private volatile boolean running = true;
	private @Nullable Connection connection;

	public TransactionExecutor(DataSource dataSource, BackoffStrategy backoffStrategy, DeadlockDetector deadlockDetector, int maxAttempts, BlockingQueue<TransactionJob<?>> jobs) {
		this.dataSource = dataSource;
		this.backoffStrategy = backoffStrategy;
		this.deadlockDetector = deadlockDetector;
		this.jobs = jobs;
		this.maxAttempts = maxAttempts;
	}

	public void stop() {
		running = false;
	}

	@SuppressWarnings("cast.unsafe")
	@Override
	public void run() {
		for (;;) {
			TransactionJob<?> job;

			try {
				if (running) {
					job = jobs.take();
				} else {
					job = jobs.poll();

					if (job == null) {
						break;
					}
				}
			} catch (InterruptedException ex) {
				continue;
			}

			execute(job);
		}

		if (!isClosed()) {
			try {
				Connection c = (@NonNull Connection) connection;
				c.close();
			} catch (SQLException ex) {
				logger.warn("Failed to close database connection cleanly:", ex);
			}
		}
	}

	private boolean isClosed() {
		if (connection == null) {
			return true;
		}

		try {
			return connection.isClosed();
		} catch (SQLException ex) {
			logger.warn("Connection isClosed check failed, assuming closed:", ex);
			return true;
		}
	}

	private void connect() throws SQLException {
		connection = dataSource.getConnection();
		connection.setAutoCommit(false);
	}

	@SuppressWarnings("cast.unsafe")
	private <T> void execute(TransactionJob<T> job) {
		Transaction<T> transaction = job.getTransaction();
		SettableFuture<T> future = job.getFuture();

		@Nullable SQLException mostRecentException = null;

		for (int i = 0; i < maxAttempts; i++) {
			T result;

			try {
				if (isClosed()) {
					connect();
				}

				@NonNull Connection c = (@NonNull Connection) connection;

				try {
					result = transaction.execute(c);
					c.commit();
				} catch (Throwable ex) {
					c.rollback();
					throw ex;
				}
			} catch (SQLException ex) {
				 if (deadlockDetector.isDeadlocked(ex)) {
					 mostRecentException = ex;

					int delay = backoffStrategy.getDelay(i);
					Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.MILLISECONDS);

					continue;
				 } else {
					future.setException(ex);
					return;
				 }
			} catch (Throwable ex) {
				future.setException(ex);
				return;
			}

			future.set(result);
			return;
		}

		future.setException((@NonNull SQLException) mostRecentException);
	}
}
