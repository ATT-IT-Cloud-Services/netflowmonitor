package gg.zema.elizon.netflowmonitor.utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class AsyncDbWriter implements AutoCloseable {

    private final IpFlowMetricsRepository repository;
    private final BlockingQueue<IpFlowMetricsRepository.Row> queue;
    private final int batchSize;
    private final int flushIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LongAdder droppedRows = new LongAdder();
    private final Thread worker;

    public AsyncDbWriter(
            IpFlowMetricsRepository repository,
            int queueCapacity,
            int batchSize,
            int flushIntervalMs
    ) {
        this.repository = repository;
        this.queue = new ArrayBlockingQueue<>(Math.max(1000, queueCapacity));
        this.batchSize = Math.max(100, batchSize);
        this.flushIntervalMs = Math.max(50, flushIntervalMs);
        this.worker = new Thread(this::runLoop, "ipflow-db-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void offer(IpFlowMetricsRepository.Row row) {
        if (!queue.offer(row)) {
            droppedRows.increment();
        }
    }


    @Override
    public void close() {
        running.set(false);
        worker.interrupt();
        try {
            worker.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        repository.close();
    }

    private void runLoop() {
        List<IpFlowMetricsRepository.Row> batch = new ArrayList<>(batchSize);
        long nextFlushAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs);

        while (running.get() || !queue.isEmpty()) {
            try {
                long waitNanos = Math.max(0L, nextFlushAt - System.nanoTime());
                IpFlowMetricsRepository.Row row = queue.poll(waitNanos, TimeUnit.NANOSECONDS);
                if (row != null) {
                    batch.add(row);
                }

                boolean flushDue = System.nanoTime() >= nextFlushAt;
                if (batch.size() >= batchSize || (flushDue && !batch.isEmpty())) {
                    repository.insertBatch(batch);
                    batch.clear();
                    nextFlushAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs);
                } else if (flushDue) {
                    nextFlushAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs);
                }
            } catch (InterruptedException ignored) {
                if (!running.get()) {
                    break;
                }
            }
        }

        if (!batch.isEmpty()) {
            repository.insertBatch(batch);
        }
    }
}

