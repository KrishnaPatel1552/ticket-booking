package com.copart.prep.checkout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class PausingRefundAttemptGate implements RefundAttemptGate {

    private final AtomicBoolean armed = new AtomicBoolean(false);
    private final AtomicInteger arrivals = new AtomicInteger();
    private volatile CountDownLatch arrived = new CountDownLatch(1);
    private volatile CountDownLatch release = new CountDownLatch(1);

    public void arm() {
        arrivals.set(0);
        arrived = new CountDownLatch(1);
        release = new CountDownLatch(1);
        armed.set(true);
    }

    public boolean awaitArrival(Duration timeout) throws InterruptedException {
        return arrived.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void release() {
        armed.set(false);
        release.countDown();
    }

    @Override
    public void beforeRefund() {
        if (!armed.get()) {
            return;
        }
        if (arrivals.incrementAndGet() != 1) {
            return;
        }
        CountDownLatch toRelease = release;
        arrived.countDown();
        try {
            toRelease.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
