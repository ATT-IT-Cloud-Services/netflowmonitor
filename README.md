# NetFlowMonitor

High-throughput NetFlow v9 collector with a multi-threaded processing pipeline.

## Pipeline

1. UDP ingest on configured NetFlow port
2. Bounded processing queue + worker pool (`processing.workerThreads`)
3. Multi-threaded decode directly into `IPFlowMetrics` rows
4. Async DB writer queue + batch inserts into `IPFlowMetrics` every 500ms (default)

## Config

The app auto-generates `config/netflowmonitor.config.json` on first start.

```json
{
  "netflowPort": 2055,
  "database": {
    "enabled": false,
    "jdbcUrl": "jdbc:mysql://127.0.0.1:3306/ignite?useSSL=false&serverTimezone=UTC",
    "username": "root",
    "password": "change-me",
    "poolSize": 10,
    "batchSize": 500,
    "databaseQueueCapacity": 20000,
    "flushIntervalMs": 500
  },
  "processing": {
    "workerThreads": 8,
    "processingQueueCapacity": 10000
  }
}
```

## Output

No per-flow console output. The process is optimized for ingest/convert/insert only.

## Run (IDE)

Run `gg.zema.elizon.netflowmonitor.SFlowMonitor`.

## Notes

- Backpressure is explicit: both processing and DB queues are bounded.
- Dropped flow counters are logged on shutdown when queues saturate.
- DB writes use HikariCP and JDBC batching for throughput.
- Rows are flushed to MySQL in batches every 500ms by default (or earlier if the batch size limit is reached).



## License

See [LICENSE.md](./LICENSE.md). Unmodified use in your own projects is permitted; modification is not.

Original `com.lumaserv.netflow` code is owned by https://github.com/LUMASERV/netflow-java and remains licensed under Apache 2.0.