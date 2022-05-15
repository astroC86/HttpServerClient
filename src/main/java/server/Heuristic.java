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
         var utilization = count.get() * 1.0 / threshold;
         return (int) (Math.max(0, 1 - utilization) * maxTimeoutMillis);
    }
}
