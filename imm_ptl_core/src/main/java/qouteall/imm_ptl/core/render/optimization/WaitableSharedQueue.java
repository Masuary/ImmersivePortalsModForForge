package qouteall.imm_ptl.core.render.optimization;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A pool shared by several consumers: one consumer clearing its view of the pool must not empty it for the others,
 * and a consumer that found it empty waits for the next element instead of polling.
 */
public class WaitableSharedQueue<T> extends ConcurrentLinkedQueue<T> {
    private final ConcurrentLinkedQueue<Runnable> waiters = new ConcurrentLinkedQueue<>();
    
    public WaitableSharedQueue(List<T> elements) {
        super(elements);
    }
    
    @Override
    public boolean offer(T element) {
        boolean isAdded = super.offer(element);
        wakeWaiters();
        return isAdded;
    }
    
    @Override
    public boolean addAll(Collection<? extends T> elements) {
        boolean isChanged = super.addAll(elements);
        wakeWaiters();
        return isChanged;
    }
    
    @Override
    public void clear() {
    }
    
    /**
     * Runs {@code wakeUp} once, when an element is added or at once if the queue is not empty.
     */
    public void waitForElement(Runnable wakeUp) {
        waiters.add(wakeUp);
        // an element added between the caller's failed poll and the line above woke nobody
        if (!isEmpty()) {
            wakeWaiters();
        }
    }
    
    private void wakeWaiters() {
        Runnable wakeUp;
        while ((wakeUp = waiters.poll()) != null) {
            wakeUp.run();
        }
    }
}
