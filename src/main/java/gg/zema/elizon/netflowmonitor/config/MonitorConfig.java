package gg.zema.elizon.netflowmonitor.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MonitorConfig(int netflowPort, DatabaseConfig database, ProcessingConfig processing) {

    public record DatabaseConfig(
            boolean enabled,
            String jdbcUrl,
            String username,
            String password,
            int poolSize,
            int batchSize,
            int queueCapacity,
            int flushIntervalMs
    ) {
    }

    public record ProcessingConfig(
            int workerThreads,
            int queueCapacity
    ) {
    }

    private static final Pattern STRING_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern INT_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern BOOL_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    public static MonitorConfig loadOrCreate(Path configPath) {
        if (!Files.exists(configPath)) {
            MonitorConfig defaultConfig = defaultConfig();
            writeConfig(configPath, defaultConfig);
            return defaultConfig;
        }

        try {
            String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            return parse(raw);
        } catch (IOException exception) {
            throw new RuntimeException("Konnte Konfiguration nicht lesen: " + configPath, exception);
        }
    }

    public static MonitorConfig defaultConfig() {
        return new MonitorConfig(
                2055,
                new DatabaseConfig(
                        false,
                        "jdbc:mysql://127.0.0.1:3306/ignite?useSSL=false&serverTimezone=UTC",
                        "root",
                        "change-me",
                        10,
                        500,
                        20000,
                        500
                ),
                new ProcessingConfig(
                        Math.max(2, Runtime.getRuntime().availableProcessors()),
                        10000
                )
        );
    }

    public static void writeConfig(Path configPath, MonitorConfig config) {
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            Files.writeString(
                    configPath,
                    config.toJson(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new RuntimeException("Konnte Konfiguration nicht schreiben: " + configPath, exception);
        }
    }

    public String toJson() {
        return "{\n" +
                "  \"netflowPort\": " + netflowPort + ",\n" +
                "  \"database\": {\n" +
                "    \"enabled\": " + database.enabled() + ",\n" +
                "    \"jdbcUrl\": \"" + escape(database.jdbcUrl()) + "\",\n" +
                "    \"username\": \"" + escape(database.username()) + "\",\n" +
                "    \"password\": \"" + escape(database.password()) + "\",\n" +
                "    \"poolSize\": " + database.poolSize() + ",\n" +
                "    \"batchSize\": " + database.batchSize() + ",\n" +
                "    \"databaseQueueCapacity\": " + database.queueCapacity() + ",\n" +
                "    \"flushIntervalMs\": " + database.flushIntervalMs() + "\n" +
                "  },\n" +
                "  \"processing\": {\n" +
                "    \"workerThreads\": " + processing.workerThreads() + ",\n" +
                "    \"processingQueueCapacity\": " + processing.queueCapacity() + "\n" +
                "  }\n" +
                "}\n";
    }

    private static MonitorConfig parse(String raw) {
        int netflowPort = intValue(raw, "netflowPort", 2055);
        boolean enabled = boolValue(raw);
        String jdbcUrl = stringValue(raw, "jdbcUrl");
        String username = stringValue(raw, "username");
        String password = stringValue(raw, "password");
        int poolSize = intValue(raw, "poolSize", 10);
        int batchSize = intValue(raw, "batchSize", 500);
        int databaseQueueCapacity = intValue(raw, "databaseQueueCapacity", 20000);
        int flushIntervalMs = intValue(raw, "flushIntervalMs", 500);
        int workerThreads = intValue(raw, "workerThreads", Math.max(2, Runtime.getRuntime().availableProcessors()));
        int processingQueueCapacity = intValue(raw, "processingQueueCapacity", 10000);

        DatabaseConfig database = new DatabaseConfig(enabled, jdbcUrl, username, password, poolSize, batchSize, databaseQueueCapacity, flushIntervalMs);
        ProcessingConfig processing = new ProcessingConfig(workerThreads, processingQueueCapacity);
        return new MonitorConfig(netflowPort, database, processing);
    }

    private static String stringValue(String raw, String key) {
        Pattern pattern = Pattern.compile(String.format(STRING_PATTERN_TEMPLATE.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private static int intValue(String raw, String key, int fallback) {
        Pattern pattern = Pattern.compile(String.format(INT_PATTERN_TEMPLATE.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(String raw) {
        Pattern pattern = Pattern.compile(String.format(BOOL_PATTERN_TEMPLATE.pattern(), Pattern.quote("enabled")), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return false;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
