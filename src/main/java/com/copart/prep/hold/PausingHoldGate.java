package com.copart.prep.hold;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class PausingHoldGate implements HoldGate {

    private final AtomicBoolean armed = new AtomicBoolean(false);
    private final AtomicInteger arrivals = new AtomicInteger();
    private volatile CountDownLatch firstArrived = new CountDownLatch(1);
    private volatile CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean writersArmed = new AtomicBoolean(false);
    private volatile CountDownLatch writersArrived = new CountDownLatch(1);
    private volatile CountDownLatch writersRelease = new CountDownLatch(1);
    private final AtomicBoolean seatLockArmed = new AtomicBoolean(false);
    private final AtomicInteger seatLockArrivals = new AtomicInteger();
    private volatile CountDownLatch seatLockArrived = new CountDownLatch(1);
    private volatile CountDownLatch seatLockRelease = new CountDownLatch(1);

    public void arm() {
        arrivals.set(0);
        firstArrived = new CountDownLatch(1);
        release = new CountDownLatch(1);
        armed.set(true);
    }

    public boolean awaitFirstArrival(Duration timeout) throws InterruptedException {
        return firstArrived.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void release() {
        armed.set(false);
        release.countDown();
    }

    public void armWriters(int expected) {
        writersArrived = new CountDownLatch(expected);
        writersRelease = new CountDownLatch(1);
        writersArmed.set(true);
    }

    public boolean awaitWriters(Duration timeout) throws InterruptedException {
        return writersArrived.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void releaseWriters() {
        writersArmed.set(false);
        writersRelease.countDown();
    }

    public void armSeatLock() {
        seatLockArrivals.set(0);
        seatLockArrived = new CountDownLatch(1);
        seatLockRelease = new CountDownLatch(1);
        seatLockArmed.set(true);
    }

    public boolean awaitSeatLock(Duration timeout) throws InterruptedException {
        return seatLockArrived.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void releaseSeatLock() {
        seatLockArmed.set(false);
        seatLockRelease.countDown();
    }

    @Override
    public void afterCustomerEventLock() {
        if (!armed.get()) {
            return;
        }
        if (arrivals.incrementAndGet() != 1) {
            return;
        }
        CountDownLatch toRelease = release;
        firstArrived.countDown();
        try {
            toRelease.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void beforeWritingSeats() {
        if (!writersArmed.get()) {
            return;
        }
        CountDownLatch toRelease = writersRelease;
        writersArrived.countDown();
        try {
            toRelease.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void afterSeatLock() {
        if (!seatLockArmed.get()) {
            return;
        }
        if (seatLockArrivals.incrementAndGet() != 1) {
            return;
        }
        CountDownLatch toRelease = seatLockRelease;
        seatLockArrived.countDown();
        try {
            toRelease.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
