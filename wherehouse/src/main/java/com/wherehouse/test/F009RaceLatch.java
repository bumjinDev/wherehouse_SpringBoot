package com.wherehouse.test;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
//@Profile("f009-test")
public class F009RaceLatch {

    private static final String TARGET_PROPERTY_ID = "F009_TARGET_A0000000000000000000";

    private CountDownLatch writerReleaseLatch = new CountDownLatch(1);

    private volatile boolean enabled = false;
    private volatile String targetPropertyId;
    private volatile boolean writerWaiting = false;

    @PostConstruct
    public void init() {
        enableFor(TARGET_PROPERTY_ID);
    }

    public synchronized void enableFor(String propertyId) {
        this.targetPropertyId = propertyId;
        this.writerReleaseLatch = new CountDownLatch(1);
        this.enabled = true;
        this.writerWaiting = false;
    }

    public void syncAwait(String propertyId) {
        if (!enabled) {
            return;
        }

        if (!propertyId.trim().equals(targetPropertyId.trim())) {
            return;
        }

        writerWaiting = true;

        try {
            boolean released = writerReleaseLatch.await(30, TimeUnit.SECONDS);
            if (!released) {
                throw new IllegalStateException("F009 테스트 latch 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("F009 테스트 대기 중 인터럽트 발생", e);
        } finally {
            writerWaiting = false;
        }
    }

    public void releaseWriterIfTargetIncluded(Collection<String> propertyIds) {
        if (!enabled) {
            return;
        }

        if (targetPropertyId == null) {
            return;
        }

        String trimmedTarget = targetPropertyId.trim();
        if (propertyIds.stream().anyMatch(id -> id.trim().equals(trimmedTarget))) {
            writerReleaseLatch.countDown();
        }
    }

    public boolean isWriterWaiting() {
        return writerWaiting;
    }

    public synchronized void disable() {
        this.enabled = false;
        this.targetPropertyId = null;
        this.writerReleaseLatch.countDown();
        this.writerWaiting = false;
    }
}
