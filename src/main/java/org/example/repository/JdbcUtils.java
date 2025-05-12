package org.example.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection pool manager for database connections.
 * Handles connection creation, validation, recycling, and cleanup.
 */
public class JdbcUtils {
    private static final Logger logger = LogManager.getLogger(JdbcUtils.class);

    // Connection pool configuration
    private static final int MIN_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 20;
    private static final long MAX_WAIT_TIME = 5000; // 5 seconds
    private static final long MAX_IDLE_TIME = 60000; // 1 minute
    private static final int CONNECTION_VALIDATION_TIMEOUT = 3; // seconds

    // Connection retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY = 500; // milliseconds

    // Maintenance configuration
    private static final long MAINTENANCE_INTERVAL = 30000; // 30 seconds
    private static final long STATS_LOGGING_INTERVAL = 300000; // 5 minutes

    // Connection pool
    private final BlockingQueue<PooledConnection> availableConnections;
    private final Set<PooledConnection> inUseConnections;
    private final Properties jdbcProps;
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    // Statistics
    private final AtomicInteger connectionRequests = new AtomicInteger(0);
    private final AtomicInteger connectionCreations = new AtomicInteger(0);
    private final AtomicInteger waitTimeouts = new AtomicInteger(0);
    private final AtomicInteger validationFailures = new AtomicInteger(0);
    private final AtomicLong cumulativeWaitTime = new AtomicLong(0);
    private final AtomicInteger longestWaitMillis = new AtomicInteger(0);

    // Thread for maintenance tasks
    private final ScheduledExecutorService maintenanceExecutor;
    private final Map<Connection, StackTraceElement[]> connectionTraces = new ConcurrentHashMap<>();

    // Flag to check if we've already tried to warn about driver issues
    private boolean driverWarningIssued = false;

    /**
     * Creates a new JdbcUtils instance with the provided database properties.
     * Initializes the connection pool and starts maintenance tasks.
     *
     * @param props Properties containing database connection settings
     */
    public JdbcUtils(Properties props) {
        this.jdbcProps = props;
        this.availableConnections = new LinkedBlockingQueue<>();
        this.inUseConnections = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-connection-maintenance");
            t.setDaemon(true);
            return t;
        });

        // Validate database configuration
        validateDatabaseConfiguration();

        // Initialize the connection pool with minimum connections
        initializeConnectionPool();

        // Schedule maintenance tasks
        startMaintenanceTasks();

        logger.info("Connection pool initialized with {} connections (max: {})",
                totalConnections.get(), MAX_POOL_SIZE);
    }

    /**
     * Validates the database configuration and checks database file for SQLite.
     * This helps identify issues early before connections are needed.
     */
    private void validateDatabaseConfiguration() {
        String url = jdbcProps.getProperty("jdbc.url");
        String user = jdbcProps.getProperty("jdbc.user");
        String pass = jdbcProps.getProperty("jdbc.pass");

        if (url == null || url.trim().isEmpty()) {
            logger.error("Database URL is missing in configuration");
            throw new IllegalArgumentException("Database URL cannot be null or empty");
        }

        logger.info("Database configuration: URL={}, User={}", url, user != null ? user : "none");

        // For SQLite databases, check if the file exists and is accessible
        if (url.startsWith("jdbc:sqlite:")) {
            try {
                String filePath = url.substring("jdbc:sqlite:".length());
                File dbFile = new File(filePath);

                if (!dbFile.exists()) {
                    logger.error("SQLite database file does not exist: {}", filePath);
                    throw new IllegalArgumentException("Database file not found: " + filePath);
                } else if (!dbFile.canRead()) {
                    logger.error("Cannot read SQLite database file: {}", filePath);
                    throw new IllegalArgumentException("Cannot read database file: " + filePath);
                } else if (!dbFile.canWrite()) {
                    logger.warn("SQLite database file is read-only: {}", filePath);
                }

                logger.info("SQLite database file validated: {}", filePath);
            } catch (Exception e) {
                logger.error("Error validating SQLite database file: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid database file configuration", e);
            }
        }

        // Load the appropriate JDBC driver
        try {
            if (url.startsWith("jdbc:sqlite:")) {
                Class.forName("org.sqlite.JDBC");
                logger.info("SQLite JDBC driver loaded");
            } else if (url.startsWith("jdbc:mysql:")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                logger.info("MySQL JDBC driver loaded");
            } else if (url.startsWith("jdbc:postgresql:")) {
                Class.forName("org.postgresql.Driver");
                logger.info("PostgreSQL JDBC driver loaded");
            } else {
                logger.warn("No specific driver loaded for URL: {}", url);
            }
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load JDBC driver for URL {}: {}", url, e.getMessage());
            throw new IllegalStateException("JDBC driver not found", e);
        }
    }

    /**
     * Initializes the connection pool with a minimum number of connections.
     */
    private void initializeConnectionPool() {
        int successfulConnections = 0;
        List<SQLException> errors = new ArrayList<>();

        logger.info("Initializing connection pool with {} initial connections", MIN_POOL_SIZE);

        for (int i = 0; i < MIN_POOL_SIZE; i++) {
            try {
                PooledConnection connection = createNewPooledConnection();
                if (connection != null) {
                    availableConnections.offer(connection);
                    successfulConnections++;
                }
            } catch (SQLException e) {
                errors.add(e);
                logger.error("Failed to create initial connection {}/{}: {}",
                        i + 1, MIN_POOL_SIZE, e.getMessage());
            }
        }

        if (successfulConnections == 0 && !errors.isEmpty()) {
            logger.error("Failed to create any initial connections. Application will likely fail!");
            // Throw the first error to prevent startup with no connections
            throw new IllegalStateException("Could not establish any database connections", errors.get(0));
        }

        logger.info("Connection pool initialized with {} connections", successfulConnections);
    }

    /**
     * Starts scheduled maintenance tasks for the connection pool.
     */
    private void startMaintenanceTasks() {
        // Task to clean up idle connections
        maintenanceExecutor.scheduleAtFixedRate(
                this::cleanupIdleConnections,
                MAINTENANCE_INTERVAL,
                MAINTENANCE_INTERVAL,
                TimeUnit.MILLISECONDS);

        // Task to log pool statistics
        maintenanceExecutor.scheduleAtFixedRate(
                this::logPoolStatistics,
                STATS_LOGGING_INTERVAL,
                STATS_LOGGING_INTERVAL,
                TimeUnit.MILLISECONDS);

        maintenanceExecutor.scheduleAtFixedRate(
                this::dumpConnectionLeaks,
                60000,  // Run after 1 minute
                300000, // Then every 5 minutes
                TimeUnit.MILLISECONDS);
    }

    /**
     * Gets a connection from the pool or creates a new one if needed.
     * Includes retry logic for failed connection attempts.
     *
     * @return A database connection
     * @throws SQLException if a database access error occurs after all retries
     */
    public Connection getConnection() throws SQLException {
        connectionRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();
        SQLException lastException = null;

        // Try multiple times to get a valid connection
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                PooledConnection pooledConn = null;

                // Try to get connection from pool first
                pooledConn = availableConnections.poll();

                if (pooledConn == null) {
                    // No connection available in pool
                    if (totalConnections.get() < MAX_POOL_SIZE) {
                        // Under max limit, create a new connection
                        try {
                            pooledConn = createNewPooledConnection();
                            connectionCreations.incrementAndGet();
                            logger.debug("Created new connection. Total: {}", totalConnections.get());
                        } catch (SQLException e) {
                            lastException = e;
                            logger.error("Failed to create new connection (attempt {}/{}): {}",
                                    attempt + 1, MAX_RETRY_ATTEMPTS, e.getMessage());

                            // Add delay between retries for new connections
                            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                                try {
                                    Thread.sleep(RETRY_DELAY * (attempt + 1)); // Exponential backoff
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            continue; // Try again
                        }
                    } else {
                        // ADD THE CHECK HERE - before waiting for a connection
                        if (totalConnections.get() >= MAX_POOL_SIZE * 0.8) {  // 80% capacity
                            logger.warn("Connection pool nearing capacity, checking for leaks");
                            dumpConnectionLeaks();
                        }

                        // Pool is at max capacity, wait for an available connection
                        logger.info("Connection pool full ({}), waiting for available connection",
                                totalConnections.get());

                        try {
                            pooledConn = availableConnections.poll(MAX_WAIT_TIME, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Interrupted while waiting for connection", e);
                        }

                        if (pooledConn == null) {
                            // Timeout waiting for connection
                            waitTimeouts.incrementAndGet();
                            lastException = new SQLException("Timeout waiting for connection");
                            logger.warn("Timeout waiting for connection (attempt {}/{})",
                                    attempt + 1, MAX_RETRY_ATTEMPTS);
                            continue; // Try again
                        }
                    }
                }

                // Validate connection before returning
                if (!isConnectionValid(pooledConn.getConnection())) {
                    validationFailures.incrementAndGet();
                    logger.warn("Connection validation failed, creating new connection");

                    // Close invalid connection
                    closeConnectionQuietly(pooledConn.getConnection());
                    pooledConn = null;

                    // Create a new connection
                    try {
                        pooledConn = createNewPooledConnection();
                    } catch (SQLException e) {
                        lastException = e;
                        logger.error("Failed to create replacement connection: {}", e.getMessage());
                        continue; // Try again
                    }
                }

                // If we got here, we have a valid connection
                pooledConn.setLastUsed(System.currentTimeMillis());
                pooledConn.setInUse(true);
                inUseConnections.add(pooledConn);

                // Update statistics
                long waitTime = System.currentTimeMillis() - startTime;
                cumulativeWaitTime.addAndGet(waitTime);
                int currentLongestWait;
                do {
                    currentLongestWait = longestWaitMillis.get();
                    if (waitTime <= currentLongestWait) break;
                } while (!longestWaitMillis.compareAndSet(currentLongestWait, (int)waitTime));

                logger.debug("Obtained connection from pool. Available: {}, In use: {}, Total: {}",
                        availableConnections.size(), inUseConnections.size(), totalConnections.get());

                connectionTraces.put(pooledConn.getConnection(), Thread.currentThread().getStackTrace());
                return pooledConn.getConnection();
            } catch (Exception e) {
                if (!(e instanceof SQLException)) {
                    // Convert to SQLException if it's not already one
                    lastException = new SQLException("Error obtaining connection: " + e.getMessage(), e);
                } else {
                    lastException = (SQLException)e;
                }

                logger.error("Error obtaining connection (attempt {}/{}): {}",
                        attempt + 1, MAX_RETRY_ATTEMPTS, e.getMessage());

                // Only delay if we're going to retry
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting to retry", ie);
                    }
                }
            }
        }

        // If we got here, all attempts failed
        if (lastException != null) {
            logger.error("Failed to obtain database connection after {} attempts: {}",
                    MAX_RETRY_ATTEMPTS, lastException.getMessage());

            // Connection pool diagnostics for troubleshooting
            logPoolStatistics();

            throw lastException;
        } else {
            throw new SQLException("Failed to obtain database connection after multiple attempts");
        }
    }

    /**
     * Releases a connection back to the pool.
     * @param conn The connection to release
     */
    public void releaseConnection(Connection conn) {
        if (conn == null) return;

        PooledConnection pooledConn = null;

        // Find the pooled connection in the in-use set
        for (PooledConnection pc : inUseConnections) {
            if (pc.getConnection() == conn) {
                pooledConn = pc;
                break;
            }
        }

        if (pooledConn != null) {
            // Mark connection as no longer in use
            pooledConn.setInUse(false);
            pooledConn.setLastUsed(System.currentTimeMillis());

            // Remove from in-use set
            inUseConnections.remove(pooledConn);
            connectionTraces.remove(conn);

            // Check if there are any pending requests in the pool
            boolean hasWaiters = !availableConnections.offer(pooledConn);
            if (hasWaiters) {
                logger.warn("Failed to return connection to pool - queue full. This should never happen!");
                // Close this connection since we couldn't return it to the pool
                closeConnectionQuietly(pooledConn.getConnection());
                totalConnections.decrementAndGet();
            } else {
                logger.debug("Released connection back to pool. Available: {}",
                        availableConnections.size());
            }
        } else {
            // Connection not in our tracking - might be from outside the pool
            logger.warn("Connection not found in pool, closing directly");
            closeConnectionQuietly(conn);
        }
    }

    /**
     * Creates a new database connection with detailed error handling
     * @return A new Connection object wrapped in a PooledConnection
     * @throws SQLException if a database access error occurs
     */
    private PooledConnection createNewPooledConnection() throws SQLException {
        String url = jdbcProps.getProperty("jdbc.url");
        String user = jdbcProps.getProperty("jdbc.user");
        String pass = jdbcProps.getProperty("jdbc.pass");

        logger.debug("Creating new connection to {} (User: {})", url, user);

        Connection conn = null;
        try {
            // Create the connection
            if (user != null && pass != null && !user.trim().isEmpty()) {
                conn = DriverManager.getConnection(url, user, pass);
            } else {
                conn = DriverManager.getConnection(url);
            }

            // Configure connection for better performance
            configureConnection(conn);

            // Increment total connections counter
            totalConnections.incrementAndGet();

            // Create and return the pooled connection
            return new PooledConnection(conn);
        } catch (SQLException e) {
            if (!driverWarningIssued && e.getMessage().contains("driver")) {
                logger.error("Database driver not found or not properly loaded. " +
                        "Make sure the appropriate JDBC driver is in your classpath.");
                driverWarningIssued = true;
            }

            logger.error("Failed to create database connection: URL={}, Error={}",
                    url, e.getMessage());

            // Check SQLite database file if relevant
            if (url.startsWith("jdbc:sqlite:")) {
                String filePath = url.substring("jdbc:sqlite:".length());
                File dbFile = new File(filePath);
                logger.error("Database file status: exists={}, readable={}, writable={}, path={}",
                        dbFile.exists(), dbFile.canRead(), dbFile.canWrite(), filePath);
            }

            // Close the connection if it was partially created
            if (conn != null) {
                closeConnectionQuietly(conn);
            }

            // Re-throw the exception to be handled by caller
            throw new SQLException("Failed to establish database connection: " + e.getMessage(), e);
        }
    }

    /**
     * Configure connection properties for optimal performance.
     * Each database type has its own optimizations.
     */
    private void configureConnection(Connection conn) throws SQLException {
        String url = jdbcProps.getProperty("jdbc.url");

        try {
            // Set database-specific performance settings
            if (url.contains("sqlite")) {
                configureSqliteConnection(conn);
            } else {
                // Generic settings for other databases
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.warn("Error configuring connection: {}", e.getMessage());
            // Don't rethrow - we can still use the connection even if optimization fails
        }
    }

    /**
     * Configure SQLite-specific connection settings
     */
    private void configureSqliteConnection(Connection conn) throws SQLException {
        boolean wasAutoCommit = conn.getAutoCommit();

        try {
            // Disable auto-commit for running multiple pragmas
            if (wasAutoCommit) {
                conn.setAutoCommit(false);
            }

            // Execute PRAGMA statements with individual error handling
            try (Statement stmt = conn.createStatement()) {
                // List of pragmas to try setting
                String[][] pragmas = {
                        {"journal_mode", "WAL"},
                        {"synchronous", "NORMAL"},
                        {"cache_size", "10000"},
                        {"mmap_size", "30000000"},
                        {"temp_store", "MEMORY"},
                        {"foreign_keys", "ON"}
                };

                for (String[] pragma : pragmas) {
                    try {
                        stmt.execute("PRAGMA " + pragma[0] + " = " + pragma[1]);
                    } catch (SQLException e) {
                        logger.warn("Failed to set SQLite PRAGMA {}: {}",
                                pragma[0], e.getMessage());
                    }
                }
            }

            // Commit pragma changes
            if (wasAutoCommit) {
                conn.commit();
                // Restore original auto-commit setting
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            logger.warn("Error configuring SQLite connection: {}", e.getMessage());

            // Try to rollback if we started a transaction
            if (wasAutoCommit) {
                try {
                    conn.rollback();
                    conn.setAutoCommit(wasAutoCommit);
                } catch (SQLException re) {
                    logger.warn("Error rolling back SQLite configuration: {}", re.getMessage());
                }
            }

            // Don't rethrow - we can still use the connection even if optimization fails
        }
    }


    /**
     * Check if a connection is still valid.
     * Uses isValid with a timeout for quick validation.
     */
    private boolean isConnectionValid(Connection conn) {
        if (conn == null) return false;

        try {
            // Quick check for closed connection
            if (conn.isClosed()) {
                return false;
            }

            // Use isValid with a timeout
            return conn.isValid(CONNECTION_VALIDATION_TIMEOUT);
        } catch (SQLException e) {
            logger.warn("Error validating connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Close a connection quietly (without throwing exceptions)
     */
    private void closeConnectionQuietly(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    // Try to rollback any pending transactions
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.rollback();
                        }
                    } catch (SQLException e) {
                        logger.error("Error rolling back transaction before closing connection", e);
                    }

                    // Now close the connection
                    conn.close();
                    logger.trace("Connection closed");
                }
            } catch (SQLException e) {
                logger.error("Error closing connection", e);
            }
        }
    }

    /**
     * Periodically clean up idle connections that have been unused
     * for longer than MAX_IDLE_TIME.
     */
    private void cleanupIdleConnections() {
        long now = System.currentTimeMillis();
        int closed = 0;

        // Take a snapshot of available connections
        List<PooledConnection> connections = new ArrayList<>();
        availableConnections.drainTo(connections);

        // Check each connection
        for (PooledConnection conn : connections) {
            // Skip connections in use
            if (conn.isInUse()) {
                availableConnections.offer(conn);
                continue;
            }

            // Check if connection has been idle too long
            if (now - conn.getLastUsed() > MAX_IDLE_TIME &&
                    totalConnections.get() > MIN_POOL_SIZE) {
                // Close idle connection if above minimum pool size
                closeConnectionQuietly(conn.getConnection());
                totalConnections.decrementAndGet();
                closed++;
            } else {
                // Return connection to the pool
                availableConnections.offer(conn);
            }
        }

        if (closed > 0) {
            logger.info("Connection pool maintenance: closed {} idle connections. " +
                            "Available: {}, Total: {}",
                    closed, availableConnections.size(), totalConnections.get());
        }
    }

    /**
     * Logs statistics about the connection pool.
     */
    private void logPoolStatistics() {
        int totalConns = totalConnections.get();
        int availableConns = availableConnections.size();
        int inUseConns = inUseConnections.size();

        int requests = connectionRequests.get();
        int creations = connectionCreations.get();
        int timeouts = waitTimeouts.get();
        int validationFails = validationFailures.get();

        long cumWaitTime = cumulativeWaitTime.get();
        int longWait = longestWaitMillis.get();

        double avgWaitTime = requests > 0 ? (double)cumWaitTime / requests : 0;

        logger.info("Connection pool stats: Total={}, Available={}, InUse={}, Requests={}, " +
                        "Created={}, Timeouts={}, ValidationFailures={}, AvgWaitTime={}ms, LongestWait={}ms",
                totalConns, availableConns, inUseConns, requests, creations, timeouts,
                validationFails, avgWaitTime, longWait);

        // Reset some counters
        connectionRequests.set(0);
        connectionCreations.set(0);
        waitTimeouts.set(0);
        validationFailures.set(0);
        cumulativeWaitTime.set(0);
        longestWaitMillis.set(0);
    }

    /**
     * Closes all connections in the pool and shuts down the maintenance executor.
     * Call this method when the application is shutting down.
     */
    public void shutdown() {
        logger.info("Application shutting down, checking for connection leaks...");
        dumpConnectionLeaks();
        closeAllConnections();
        totalConnections.set(0);
        logger.info("Database connection pool shut down");
    }

    /**
     * Closes all connections in the pool.
     * This method should be called when shutting down the application
     * or when you need to force all connections to close (e.g., during
     * database maintenance operations).
     *
     * @return The number of connections closed
     */
    public int closeAllConnections() {
        logger.info("Closing all database connections in the pool");
        int closedCount = 0;

        // First, get all available connections from the pool
        List<PooledConnection> availableConns = new ArrayList<>();
        availableConnections.drainTo(availableConns);

        // Close each available connection
        for (PooledConnection conn : availableConns) {
            closeConnectionQuietly(conn.getConnection());
            closedCount++;
        }

        // Log warning about any in-use connections
        int inUseCount = inUseConnections.size();
        if (inUseCount > 0) {
            logger.warn("{} connections are still in use while attempting to close all connections",
                    inUseCount);

            // Optionally force-close in-use connections (this could cause issues in active operations)
            for (PooledConnection conn : new ArrayList<>(inUseConnections)) {
                logger.warn("Force-closing in-use connection");
                closeConnectionQuietly(conn.getConnection());
                inUseConnections.remove(conn);
                closedCount++;
            }
        }

        // Reset the total connections counter to match actual state
        totalConnections.set(0);

        // Log summary
        logger.info("Closed {} database connections", closedCount);

        return closedCount;
    }

    public void dumpConnectionLeaks() {
        if (connectionTraces.isEmpty()) {
            logger.info("No connection leaks detected");
            return;
        }

        logger.warn("Found {} potential connection leaks:", connectionTraces.size());
        int count = 0;
        for (Map.Entry<Connection, StackTraceElement[]> entry : connectionTraces.entrySet()) {
            count++;
            logger.warn("Leak #{} - Connection obtained at:", count);
            for (int i = 0; i < Math.min(10, entry.getValue().length); i++) {
                logger.warn("  at {}", entry.getValue()[i]);
            }
        }
    }

    /**
     * Inner class to track connection state
     */
    private static class PooledConnection {
        private final Connection connection;
        private long lastUsed;
        private boolean inUse;

        public PooledConnection(Connection connection) {
            this.connection = connection;
            this.lastUsed = System.currentTimeMillis();
            this.inUse = false;
        }

        public Connection getConnection() {
            return connection;
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public void setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
        }

        public boolean isInUse() {
            return inUse;
        }

        public void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
    }
}