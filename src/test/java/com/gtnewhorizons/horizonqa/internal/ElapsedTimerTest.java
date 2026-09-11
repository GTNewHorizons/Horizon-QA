package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.google.common.base.Ticker;
import com.gtnewhorizons.horizonqa.report.ElapsedTime;

public class ElapsedTimerTest {

    @Test
    public void elapsedIncludesAllWaitingAndFreezesOnInterruption() {
        AtomicLong nanos = new AtomicLong();
        ElapsedTimer timer = new ElapsedTimer(new Ticker() {

            @Override
            public long read() {
                return nanos.get();
            }
        });
        assertEquals(
            ElapsedTime.State.UNAVAILABLE,
            timer.snapshot()
                .state());
        timer.start();
        nanos.set(2_500_000_000L);
        assertEquals(
            2.5,
            timer.snapshot()
                .seconds(),
            0);
        assertEquals(
            ElapsedTime.State.RUNNING,
            timer.snapshot()
                .state());
        timer.finish(true);
        nanos.set(9_000_000_000L);
        assertEquals(
            2.5,
            timer.snapshot()
                .seconds(),
            0);
        assertEquals(
            ElapsedTime.State.PARTIAL,
            timer.snapshot()
                .state());
    }
}
