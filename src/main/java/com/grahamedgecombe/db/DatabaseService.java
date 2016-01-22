package com.grahamedgecombe.db;

import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sql.DataSource;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import org.checkerframework.checker.lock.qual.GuardedBy;

/**
 * The core class of the database API, which manages the pool of transaction
 * execution threads and the queue of pending transactions.
 * @author Graham Edgecombe
 */
public final class DatabaseService extends AbstractService {
	private static final BackoffStrategy DEFAULT_BACKOFF_STRATEGY = new BinaryExponentialBackoffStrategy(8, 10);
	private static final DeadlockDetector DEFAULT_DEADLOCK_DETECTOR = ex -> true;
	private static final int DEFAULT_MAX_ATTEMPTS = 5;
	private static final int DEFAULT_THREADS = 1;

	/**
	 * Creates a new {@link Builder} object.
	 * @param dataSource The JDBC data source.
	 * @return The builder.
	 */
	public static Builder builder(DataSource dataSource) {
		return new Builder(dataSource);
	}

	/**
	 * Creates {@link DatabaseService} objects.
	 */
	public static final class Builder {
		private final DataSource dataSource;
		private BackoffStrategy backoffStrategy = DEFAULT_BACKOFF_STRATEGY;
		private DeadlockDetector deadlockDetector = DEFAULT_DEADLOCK_DETECTOR;
		private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
		private int threads = DEFAULT_THREADS;

		private Builder(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		/**
		 * Sets the {@link BackoffStrategy} of the {@link DatabaseService}
		 * object currently being built.
		 *
		 * The default {@link BackoffStrategy} uses binary exponential backoff
		 * with a {@code cMax} value of {@code 8} and a {@code scale} value of
		 * {@code 10} milliseconds.
		 * @param backoffStrategy The {@link BackoffStrategy}.
		 * @return This {@link Builder} object, for method chaining.
		 */
		public Builder setBackoffStrategy(BackoffStrategy backoffStrategy) {
			this.backoffStrategy = backoffStrategy;
			return this;
		}

		/**
		 * Sets the {@link DeadlockDetector} of the {@link DatabaseService}
		 * object currently being built.
		 *
		 * The default {@link DeadlockDetector} simply returns {@code true} for
		 * all types of {@link SQLException}, for the broadest compatibility
		 * across database servers.
		 * @param deadlockDetector The {@link DeadlockDetector}.
		 * @return This {@link Builder} object, for method chaining.
		 */
		public Builder setDeadlockDetector(DeadlockDetector deadlockDetector) {
			this.deadlockDetector = deadlockDetector;
			return this;
		}

		/**
		 * Sets the maximum number of transaction attempts.
		 *
		 * The default is 5 attempts.
		 * @param maxAttempts The maximum number of attempts before marking a
		 *                    transaction as failed.
		 * @throws IllegalArgumentException if {@code maxAttempts} is zero or
		 *                                  negative.
		 * @return This {@link Builder} object, for method chaining.
		 */
		public Builder setMaxAttempts(int maxAttempts) {
			Preconditions.checkArgument(maxAttempts > 0, "maxAttempts must be positive");
			this.maxAttempts = maxAttempts;
			return this;
		}

		/**
		 * Sets the number of transaction execution threads. Each thread
		 * controls a single connection.
		 *
		 * The default is 1 thread.
		 * @param threads The number of transaction execution threads.
		 * @throws IllegalArgumentException if {@code threads} is zero or
		 *                                  negative.
		 * @return This {@link Builder} object, for method chaining.
		 */
		public Builder setThreads(int threads) {
			Preconditions.checkArgument(threads > 0, "threads must be positive");
			this.threads = threads;
			return this;
		}

		/**
		 * Builds a new {@link DatabaseService} object.
		 * @return The {@link DatabaseService} object.
		 */
		public DatabaseService build() {
			return new DatabaseService(dataSource, backoffStrategy, deadlockDetector, maxAttempts, threads);
		}
	}

	private static TransactionExecutor[] createExecutors(DataSource dataSource, BackoffStrategy backoffStrategy, DeadlockDetector deadlockDetector, int maxAttempts, int threads, BlockingQueue<TransactionJob<?>> jobs) {
		TransactionExecutor[] executors = new TransactionExecutor[threads];

		for (int i = 0; i < executors.length; i++) {
			executors[i] = new TransactionExecutor(dataSource, backoffStrategy, deadlockDetector, maxAttempts, jobs);
		}

		return executors;
	}

	private static Thread[] createThreads(TransactionExecutor[] executors) {
		Thread[] threads = new Thread[executors.length];

		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(executors[i]);
		}

		return threads;
	}

	private final Object lock = new Object();
	private final BlockingQueue<TransactionJob<?>> jobs = new LinkedBlockingQueue<>();
	private final TransactionExecutor[] executors;
	private final Thread[] threads;
	private @GuardedBy("lock") boolean running;

	private DatabaseService(DataSource dataSource, BackoffStrategy backoffStrategy, DeadlockDetector deadlockDetector, int maxAttempts, int threads) {
		this.executors = createExecutors(dataSource, backoffStrategy, deadlockDetector, maxAttempts, threads, jobs);
		this.threads = createThreads(executors);
	}

	/**
	 * Starts the {@link DatabaseService}. This method blocks until the
	 * {@link DatabaseService} is running.
	 * @return This {@link DatabaseService} object, for method chaining.
	 */
	public DatabaseService start() {
		startAsync().awaitRunning();
		return this;
	}

	/**
	 * Stops the {@link DatabaseService}. Any transactions submitted before the
	 * {@link #stop()} method is called will be executed. This method blocks
	 * until the {@link DatabaseService} has stopped.
	 */
	public void stop() {
		stopAsync().awaitTerminated();
	}

	/**
	 * Executes a {@link Transaction} asynchronously.
	 * @param transaction The transaction.
	 * @param <T> The type of result returned by the transaction.
	 * @throws IllegalStateException if this {@link DatabaseService} is not
	 *                               in the running state.
	 * @return A {@link ListenableFuture} representing the result of the
	 *         transaction.
	 */
	public <T> ListenableFuture<T> executeAsync(Transaction<T> transaction) {
		SettableFuture<T> future = SettableFuture.create();

		synchronized (lock) {
			Preconditions.checkState(running);
			jobs.add(new TransactionJob<>(future, transaction));
		}

		return future;
	}

	/**
	 * Executes a {@link Transaction} synchronously.
	 * @param transaction The transaction.
	 * @param <T> The type of result returned by the transaction.
	 * @return The result of the transaction.
	 * @throws IllegalStateException if this {@link DatabaseService} is not
	 *                               in the running state.
	 * @throws ExecutionException if an exception was thrown during execution
	 *                            of the transaction.
	 */
	public <T> T execute(Transaction<T> transaction) throws ExecutionException {
		return Uninterruptibles.getUninterruptibly(executeAsync(transaction));
	}

	/**
	 * Executes a {@link VoidTransaction} asynchronously.
	 * @param transaction The transaction.
	 * @throws IllegalStateException if this {@link DatabaseService} is not
	 *                               in the running state.
	 * @return A {@link ListenableFuture} representing the result of the
	 *         transaction.
	 */
	public ListenableFuture<Void> executeVoidAsync(VoidTransaction transaction) {
		return executeAsync(connection -> {
			transaction.execute(connection);
			return null;
		});
	}

	/**
	 * Executes a {@link VoidTransaction} synchronously.
	 * @param transaction The transaction.
	 * @throws IllegalStateException if this {@link DatabaseService} is not
	 *                               in the running state.
	 * @throws ExecutionException if an exception was thrown during execution
	 *                            of the transaction.
	 */
	public void executeVoid(VoidTransaction transaction) throws ExecutionException {
		Uninterruptibles.getUninterruptibly(executeVoidAsync(transaction));
	}

	@Override
	protected void doStart() {
		for (Thread thread : threads) {
			thread.start();
		}

		synchronized (lock) {
			running = true;
		}

		notifyStarted();
	}

	@Override
	protected void doStop() {
		synchronized (lock) {
			running = false;
		}

		for (TransactionExecutor executor : executors) {
			executor.stop();
		}

		for (Thread thread : threads) {
			thread.interrupt();
		}

		for (Thread thread : threads) {
			Uninterruptibles.joinUninterruptibly(thread);
		}

		notifyStopped();
	}
}
