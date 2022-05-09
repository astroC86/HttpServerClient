package server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Heuristic implements Supplier<Integer> {
    private AtomicInteger count;
    private int threshold;
    private int maxTimeoutMillis;

    public Heuristic(AtomicInteger count, int threshold, int maxTimeoutMillis) {
        this.count = count;
        this.threshold = threshold;
        this.maxTimeoutMillis = maxTimeoutMillis;
    }

    @Override
    public Integer get() {
         var utilization = (threshold - count.get()) * 1.0 / threshold;
         return (int) (utilization * maxTimeoutMillis);
    }
}
