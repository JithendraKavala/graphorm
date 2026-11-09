package com.graphorm.jdbc.event;

import com.graphorm.core.event.EntityEvent;
import com.graphorm.core.event.EntityEventBroker;
import com.graphorm.core.event.EntityEventCodec;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background daemon listener listening for PostgreSQL {@code NOTIFY} events on a specified channel.
 * Reads notifications via {@link org.postgresql.PGConnection#getNotifications(int)} and publishes
 * decoded {@link EntityEvent} instances to an {@link EntityEventBroker}.
 */
public class PostgresNotificationListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotificationListener.class);

    private final DataSource dataSource;
    private final String channelName;
    private final EntityEventBroker eventBroker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;

    public PostgresNotificationListener(DataSource dataSource, String channelName, EntityEventBroker eventBroker) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.channelName = (channelName != null && !channelName.isBlank()) ? channelName.trim() : "graphorm_events";
        this.eventBroker = Objects.requireNonNull(eventBroker, "EntityEventBroker must not be null");
    }

    /**
     * Checks if the PostgreSQL JDBC driver class is available on classpath.
     */
    public static boolean isDriverAvailable() {
        try {
            Class.forName("org.postgresql.PGConnection");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Starts the background listener daemon thread.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            listenerThread = new Thread(this::listenLoop, "graphorm-pg-listener-" + channelName);
            listenerThread.setDaemon(true);
            listenerThread.start();
            log.info("Started PostgresNotificationListener daemon on channel '{}'", channelName);
        }
    }

    /**
     * Stops the background listener daemon thread.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (listenerThread != null) {
                listenerThread.interrupt();
            }
            log.info("Stopped PostgresNotificationListener daemon on channel '{}'", channelName);
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get();
    }

    public String channelName() {
        return channelName;
    }

    public EntityEventBroker eventBroker() {
        return eventBroker;
    }

    private void listenLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = dataSource.getConnection();
                PGConnection pgConn;
                if (conn.isWrapperFor(PGConnection.class)) {
                    pgConn = conn.unwrap(PGConnection.class);
                } else if (conn instanceof PGConnection pgc) {
                    pgConn = pgc;
                } else {
                    log.error("Underlying JDBC connection does not support PGConnection. Disabling PostgresNotificationListener.");
                    running.set(false);
                    return;
                }

                stmt = conn.createStatement();
                stmt.execute("LISTEN " + channelName);
                log.debug("Executing LISTEN on channel '{}'", channelName);

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    PGNotification[] notifications = pgConn.getNotifications(500);
                    if (notifications != null && notifications.length > 0) {
                        for (PGNotification n : notifications) {
                            if (n != null && channelName.equalsIgnoreCase(n.getName())) {
                                String payload = n.getParameter();
                                if (payload != null && !payload.isBlank()) {
                                    handleNotificationPayload(payload);
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                if (running.get()) {
                    log.warn("PostgresNotificationListener encountered SQLException on channel '{}': {}. Reconnecting in 1s...",
                            channelName, e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Throwable t) {
                if (running.get()) {
                    log.error("Unexpected error in PostgresNotificationListener loop: {}", t.getMessage(), t);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (SQLException ignored) {}
                }
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException ignored) {}
                }
            }
        }
    }

    private void handleNotificationPayload(String payload) {
        try {
            EntityEvent event = EntityEventCodec.fromJson(payload);
            eventBroker.publish(event);
        } catch (Exception e) {
            log.error("Failed to decode or publish entity event from PostgreSQL NOTIFY payload: {}", payload, e);
        }
    }
}
