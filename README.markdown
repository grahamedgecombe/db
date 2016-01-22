Database API
============

A thin layer on top of the JDBC API that takes care of awkward boilerplate
code, such as retrying transactions that are aborted because of a deadlock and
reconnecting to the database server if the connection is lost.

Features
--------

 * Enforces the use of transactions.
 * Automatically re-connects the database if the connection is lost (e.g. due
   to a timeout.)
 * Automatically retries transactions if they fail due to deadlock. Deadlocks
   are a [normal][mysql-deadlock] [occurrence][pg-deadlock] in many database
   servers and it is the user's responsibility to retry transactions that are
   aborted because of a deadlock. The maximum number of attempts and delay
   between attempts is configurable.
 * Maintains a configurable, fixed-size pool of open connections.
 * Provides both a synchronous and an asynchronous API.

Installation
------------

This project is available in the [Maven][mvn] Central Repository. Add the
following dependency to your `pom.xml` file to use it:

    <dependency>
      <groupId>com.grahamedgecombe.db</groupId>
      <artifactId>db</artifactId>
      <version>1.0.0</version>
    </dependency>

Usage
-----

### Synchronous API

    DataSource dataSource = ...;
    DatabaseService service = DatabaseService.builder(dataSource).build().start();
    
    try {
        String result = service.execute(connection -> {
            try (PreparedStmt stmt = connection.prepareStatement("SELECT 'hello, world';")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getString(1);
                }
            }
        });
    
        System.out.println(result); // prints "hello, world"
    } catch (ExecutionException ex) {
        ex.printStackTrace();
    }
    
    service.stop();

### Asynchronous API

    DataSource dataSource = ...;
    DatabaseService service = DatabaseService.builder(dataSource).build().start();
    
    ListenableFuture<String> future = service.executeAsync(connection -> {
        try (PreparedStmt stmt = connection.prepareStatement("SELECT 'hello, world';")) {
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    });
    
    Futures.addCallback(future, new FutureCallback<String>() {
        @Override
        public void onSuccess(String result) {
            System.out.println(result); // prints "hello, world"
        }
    
        @Override
        public void onFailure(Throwable ex) {
            ex.printStackTrace();
        }
    });
    
    service.stop(); // any transactions submitted before stop() are executed before stop() returns

Dependencies
------------

 * Java 8 or above
 * [Google Guava][guava]
 * [SLF4J][slf4j]

License
-------

This project is available under the terms of the [ISC license][isc], which is
similar to the 2-clause BSD license. See the `LICENSE` file for the copyright
information and licensing terms.

[mvn]: https://maven.apache.org/
[isc]: http://opensource.org/licenses/isc-license.txt
[guava]: https://github.com/google/guava
[slf4j]: http://slf4j.org/
[mysql-deadlock]: https://dev.mysql.com/doc/refman/5.7/en/innodb-deadlock-detection.html
[pg-deadlock]: http://www.postgresql.org/docs/current/static/explicit-locking.html#LOCKING-DEADLOCKS
