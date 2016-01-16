package com.grahamedgecombe.db;

import com.google.common.util.concurrent.SettableFuture;

final class TransactionJob<T> {
	private final SettableFuture<T> future;
	private final Transaction<T> transaction;

	public TransactionJob(SettableFuture<T> future, Transaction<T> transaction) {
		this.future = future;
		this.transaction = transaction;
	}

	public SettableFuture<T> getFuture() {
		return future;
	}

	public Transaction<T> getTransaction() {
		return transaction;
	}
}
