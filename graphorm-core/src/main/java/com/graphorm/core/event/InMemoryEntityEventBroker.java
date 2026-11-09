package com.graphorm.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Thread-safe, high-performance in-memory implementation of {@link EntityEventBroker}.
 * Used for non-PostgreSQL dialects (SQLite, MySQL) and unit test environments.
 */
public class InMemoryEntityEventBroker implements EntityEventBroker {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEntityEventBroker.class);

    private final CopyOnWriteArrayList<EventSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Executor executor;

    public InMemoryEntityEventBroker() {
        this(Runnable::run);
    }

    public InMemoryEntityEventBroker(Executor executor) {
        this.executor = executor != null ? executor : Runnable::run;
    }

    @Override
    public void publish(EntityEvent event) {
        Objects.requireNonNull(event, "EntityEvent must not be null");
        if (closed.get()) {
            log.warn("Attempted to publish event to a closed InMemoryEntityEventBroker: {}", event);
            return;
        }

        for (EventSubscription sub : subscriptions) {
            if (sub.isCancelled()) {
                subscriptions.remove(sub);
                continue;
            }
            if (sub.matches(event)) {
                sub.deliver(event, executor);
            }
        }
    }

    @Override
    public Flow.Publisher<EntityEvent> publisher() {
        return subscriber -> registerSubscriber(subscriber, event -> true);
    }

    @Override
    public Flow.Publisher<EntityEvent> publisher(String entityName) {
        Objects.requireNonNull(entityName, "entityName must not be null");
        return subscriber -> registerSubscriber(subscriber, event -> entityName.equalsIgnoreCase(event.entityName()));
    }

    @Override
    public Flow.Publisher<EntityEvent> publisher(String entityName, NotifyEventType eventType) {
        Objects.requireNonNull(entityName, "entityName must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        return subscriber -> registerSubscriber(subscriber,
                event -> entityName.equalsIgnoreCase(event.entityName()) && event.eventType() == eventType);
    }

    private void registerSubscriber(Flow.Subscriber<? super EntityEvent> subscriber, Predicate<EntityEvent> filter) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        if (closed.get()) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {}
            });
            subscriber.onComplete();
            return;
        }

        EventSubscription sub = new EventSubscription(subscriber, filter, this::removeSubscription);
        subscriptions.add(sub);
        subscriber.onSubscribe(sub);
    }

    private void removeSubscription(EventSubscription sub) {
        subscriptions.remove(sub);
    }

    public int activeSubscriptionCount() {
        return subscriptions.size();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (EventSubscription sub : subscriptions) {
                sub.complete();
            }
            subscriptions.clear();
        }
    }

    private static class EventSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super EntityEvent> subscriber;
        private final Predicate<EntityEvent> filter;
        private final java.util.function.Consumer<EventSubscription> onCancel;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        EventSubscription(
                Flow.Subscriber<? super EntityEvent> subscriber,
                Predicate<EntityEvent> filter,
                java.util.function.Consumer<EventSubscription> onCancel
        ) {
            this.subscriber = subscriber;
            this.filter = filter;
            this.onCancel = onCancel;
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        boolean matches(EntityEvent event) {
            return filter.test(event);
        }

        void deliver(EntityEvent event, Executor executor) {
            if (cancelled.get()) {
                return;
            }
            long currentReq = requested.get();
            if (currentReq > 0) {
                requested.decrementAndGet();
                executor.execute(() -> {
                    try {
                        subscriber.onNext(event);
                    } catch (Throwable t) {
                        log.error("Subscriber threw error in onNext: {}", t.getMessage(), t);
                    }
                });
            } else {
                log.debug("Dropping event for subscriber due to lack of demand: {}", event);
            }
        }

        void complete() {
            if (cancelled.compareAndSet(false, true)) {
                try {
                    subscriber.onComplete();
                } catch (Throwable t) {
                    log.error("Subscriber threw error in onComplete: {}", t.getMessage(), t);
                }
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("Request demand must be positive: " + n));
                return;
            }
            requested.updateAndGet(current -> {
                long updated = current + n;
                return updated < 0 ? Long.MAX_VALUE : updated;
            });
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                onCancel.accept(this);
            }
        }
    }
}
