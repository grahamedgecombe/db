package com.grahamedgecombe.db;

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

public final class DatabaseService extends AbstractService {
	private static final BackoffStrategy DEFAULT_BACKOFF_STRATEGY = new BinaryExponentialBackoffStrategy(8, 10);
	private static final DeadlockDetector DEFAULT_DEADLOCK_DETECTOR = ex -> true;
	private static final int DEFAULT_MAX_ATTEMPTS = 5;
	private static final int DEFAULT_THREADS = 1;

	public static Builder builder(DataSource dataSource) {
		return new Builder(dataSource);
	}

	public static final class Builder {
		private final DataSource dataSource;
		private BackoffStrategy backoffStrategy = DEFAULT_BACKOFF_STRATEGY;
		private DeadlockDetector deadlockDetector = DEFAULT_DEADLOCK_DETECTOR;
		private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
		private int threads = DEFAULT_THREADS;

		private Builder(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		public Builder setBackoffStrategy(BackoffStrategy backoffStrategy) {
			this.backoffStrategy = backoffStrategy;
			return this;
		}

		public Builder setDeadlockDetector(DeadlockDetector deadlockDetector) {
			this.deadlockDetector = deadlockDetector;
			return this;
		}

		public Builder setMaxAttempts(int maxAttempts) {
			Preconditions.checkArgument(maxAttempts > 0, "maxAttempts must be positive");
			this.maxAttempts = maxAttempts;
			return this;
		}

		public Builder setThreads(int threads) {
			Preconditions.checkArgument(threads > 0, "threads must be positive");
			this.threads = threads;
			return this;
		}

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

	public DatabaseService start() {
		startAsync().awaitRunning();
		return this;
	}

	public void stop() {
		stopAsync().awaitTerminated();
	}

	public <T> ListenableFuture<T> executeAsync(Transaction<T> transaction) {
		SettableFuture<T> future = SettableFuture.create();

		synchronized (lock) {
			Preconditions.checkState(running);
			jobs.add(new TransactionJob<>(future, transaction));
		}

		return future;
	}

	public <T> T execute(Transaction<T> transaction) throws ExecutionException {
		return Uninterruptibles.getUninterruptibly(executeAsync(transaction));
	}

	public ListenableFuture<Void> executeVoidAsync(VoidTransaction transaction) {
		return executeAsync(connection -> {
			transaction.execute(connection);
			return null;
		});
	}

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
