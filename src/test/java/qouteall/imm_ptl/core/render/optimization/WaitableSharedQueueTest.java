package qouteall.imm_ptl.core.render.optimization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitableSharedQueueTest {
    @Test
    void clearingDoesNotEmptyThePoolOfTheOtherConsumers() {
        WaitableSharedQueue<String> pool = new WaitableSharedQueue<>(List.of("a", "b"));
        pool.clear();
        assertEquals(2, pool.size());
    }

    @Test
    void aWaiterIsWokenOnceByTheNextAdd() {
        WaitableSharedQueue<String> pool = new WaitableSharedQueue<>(List.of());
        AtomicInteger wakeUps = new AtomicInteger();
        pool.waitForElement(wakeUps::incrementAndGet);
        assertEquals(0, wakeUps.get());
        pool.add("a");
        pool.add("b");
        assertEquals(1, wakeUps.get());
    }

    @Test
    void offerAndAddAllWakeWaitersToo() {
        WaitableSharedQueue<String> pool = new WaitableSharedQueue<>(List.of());
        AtomicInteger wakeUps = new AtomicInteger();
        pool.waitForElement(wakeUps::incrementAndGet);
        pool.offer("a");
        pool.poll();
        pool.waitForElement(wakeUps::incrementAndGet);
        pool.addAll(List.of("b", "c"));
        assertEquals(2, wakeUps.get());
    }

    @Test
    void aWaiterIsWokenAtOnceWhenAnElementIsAlreadyBack() {
        WaitableSharedQueue<String> pool = new WaitableSharedQueue<>(List.of("a"));
        AtomicInteger wakeUps = new AtomicInteger();
        pool.waitForElement(wakeUps::incrementAndGet);
        assertEquals(1, wakeUps.get());
    }

    @Test
    void noWaiterIsLostWhileElementsComeAndGoOnOtherThreads() throws InterruptedException {
        WaitableSharedQueue<Integer> pool = new WaitableSharedQueue<>(List.of());
        int waiterCount = 2000;
        CountDownLatch woken = new CountDownLatch(waiterCount);
        Thread producer = new Thread(() -> {
            for (int i = 0; i < waiterCount; i++) {
                pool.add(i);
                pool.poll();
            }
        });
        producer.start();
        for (int i = 0; i < waiterCount; i++) {
            pool.waitForElement(woken::countDown);
        }
        producer.join();
        pool.add(-1);
        assertTrue(woken.await(5, TimeUnit.SECONDS));
    }
}
