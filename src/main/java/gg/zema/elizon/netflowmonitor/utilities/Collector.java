package gg.zema.elizon.netflowmonitor.utilities;

import com.lumaserv.netflow.NetFlowCollector;
import com.lumaserv.netflow.NetFlowSession;
import com.lumaserv.netflow.flowset.FlowField;
import com.lumaserv.netflow.flowset.FlowValue;
import gg.zema.elizon.netflowmonitor.config.MonitorConfig;

import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

public class Collector {

    private static final long BACKLOG_MAX_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final MonitorConfig config;
    private final FlowDecoder decoder = new FlowDecoder();
    private final ExecutorService workerPool;
    private final AsyncDbWriter asyncDbWriter;
    private final ConcurrentLinkedQueue<BacklogItem> backlog = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean backlogRunning = new AtomicBoolean(true);
    private final Thread backlogDrainer;
    private final LongAdder droppedExpiredBacklog = new LongAdder();

    public Collector(MonitorConfig config) {
        this.config = config;

        MonitorConfig.ProcessingConfig processing = config.processing();
        int workerThreads = Math.max(2, processing.workerThreads());
        int queueCapacity = Math.max(workerThreads * 100, processing.queueCapacity());
        BlockingQueue<Runnable> processingQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workerPool = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                processingQueue,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("flow-worker-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                (ignoredRunnable, ignoredExecutor) -> {
                }
        );

        IpFlowMetricsRepository metricsRepository = IpFlowMetricsRepository.fromConfig(config.database()).orElse(null);
        if (metricsRepository != null) {
            MonitorConfig.DatabaseConfig database = config.database();
            this.asyncDbWriter = new AsyncDbWriter(
                    metricsRepository,
                    database.queueCapacity(),
                    database.batchSize(),
                    database.flushIntervalMs()
            );
        } else {
            throw new IllegalStateException("DB-Export ist nicht konfiguriert. Setze config.database.enabled=true und valide DB-Credentials.");
        }

        this.backlogDrainer = new Thread(this::drainBacklogLoop, "flow-backlog-drainer");
        this.backlogDrainer.setDaemon(true);
        this.backlogDrainer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownPipeline));
    }

    public void startCollecting() {

        NetFlowSession session = new NetFlowSession(source -> source.listen((templateId, values) -> {
            if (templateId == 258 || templateId == 259) {
                submitFlow(values);
            }
        }));

        try {
            NetFlowCollector collector = new NetFlowCollector(session, config.netflowPort());
            collector.join();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    private void submitFlow(Map<FlowField, FlowValue> values) {
        Map<FlowField, FlowValue> immutableFlow = Map.copyOf(values);
        if (!trySubmit(immutableFlow)) {
            backlog.offer(new BacklogItem(immutableFlow, System.nanoTime()));
        }
    }

    private boolean trySubmit(Map<FlowField, FlowValue> flowValues) {
        try {
            workerPool.execute(() -> handleFlow(flowValues));
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private void handleFlow(Map<FlowField, FlowValue> values) {
        asyncDbWriter.offer(decoder.decode(values));
    }

    private void drainBacklogLoop() {
        while (backlogRunning.get() || !backlog.isEmpty()) {
            BacklogItem next = backlog.peek();
            if (next == null) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                continue;
            }

            long now = System.nanoTime();
            if (now - next.createdAtNanos() > BACKLOG_MAX_AGE_NANOS) {
                backlog.poll();
                droppedExpiredBacklog.increment();
                continue;
            }

            if (trySubmit(next.values())) {
                backlog.poll();
                continue;
            }

            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private void shutdownPipeline() {
        backlogRunning.set(false);
        backlogDrainer.interrupt();
        try {
            backlogDrainer.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (asyncDbWriter != null) {
            asyncDbWriter.close();
        }
    }

    private record BacklogItem(Map<FlowField, FlowValue> values, long createdAtNanos) {
    }

}
