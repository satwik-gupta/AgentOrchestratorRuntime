package com.agentorchestrator.network;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentStreamPublisher implements Flow.Publisher<String>, AutoCloseable {
    private final List<AgentStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void subscribe(Flow.Subscriber<? super String> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        AgentStreamSubscription sub = new AgentStreamSubscription(subscriber);
        subscriptions.add(sub);
        subscriber.onSubscribe(sub);
    }

    public void publish(String item) {
        if (closed.get()) return;
        for (AgentStreamSubscription sub : subscriptions) {
            sub.offer(item);
        }
    }

    public void complete() {
        if (!closed.compareAndSet(false, true)) return;
        for (AgentStreamSubscription sub : subscriptions) {
            sub.complete();
        }
        executor.shutdown();
    }

    @Override
    public void close() {
        complete();
    }

    private final class AgentStreamSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super String> subscriber;
        private final AtomicLong requested = new AtomicLong(0);
        private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        AgentStreamSubscription(Flow.Subscriber<? super String> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("n must be > 0"));
                return;
            }
            requested.getAndAdd(n);
            drain();
        }

        private void drain() {
            executor.submit(() -> {
                while (!cancelled.get()) {
                    long r = requested.get();
                    if (r <= 0) break;
                    String item = buffer.poll();
                    if (item == null) break;
                    if (requested.get() <= 0) break;
                    requested.decrementAndGet();
                    try {
                        subscriber.onNext(item);
                    } catch (Throwable t) {
                        subscriber.onError(t);
                        cancel();
                        break;
                    }
                }
            });
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            subscriptions.remove(this);
        }

        void offer(String item) {
            if (cancelled.get()) return;
            if (requested.get() > 0) {
                executor.submit(() -> {
                    if (cancelled.get()) return;
                    if (requested.get() <= 0) {
                        buffer.add(item);
                        return;
                    }
                    requested.decrementAndGet();
                    try {
                        subscriber.onNext(item);
                    } catch (Throwable t) {
                        subscriber.onError(t);
                        cancel();
                    }
                });
            } else {
                buffer.add(item);
            }
        }

        void complete() {
            executor.submit(() -> {
                if (!cancelled.get()) {
                    subscriber.onComplete();
                }
            });
        }
    }
}
