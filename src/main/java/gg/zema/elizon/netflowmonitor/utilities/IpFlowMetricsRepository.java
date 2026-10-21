package gg.zema.elizon.netflowmonitor.utilities;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.zema.elizon.netflowmonitor.config.MonitorConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class IpFlowMetricsRepository implements AutoCloseable {

    public record Row(
            Long fromIPv4,
            byte[] fromIPv6,
            Long toIPv4,
            byte[] toIPv6,
            long byteSize,
            Long nextHopIPv4,
            byte[] nextHopIPv6,
            int protocol,
            int srcPort,
            int dstPort,
            int pkts
    ) {
    }

    private static final String INSERT_SQL = """
            INSERT INTO `IPFlowMetrics` (
                `fromIPv4`,
                `fromIPv6`,
                `toIPv4`,
                `toIPv6`,
                `byteSize`,
                `nextHopIPv4`,
                `nextHopIPv6`,
                `protocol`,
                `srcPort`,
                `dstPort`,
                `pkts`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final HikariDataSource dataSource;

    private IpFlowMetricsRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Optional<IpFlowMetricsRepository> fromConfig(MonitorConfig.DatabaseConfig databaseConfig) {
        if (databaseConfig == null || !databaseConfig.enabled()) {
            return Optional.empty();
        }

        String jdbcUrl = databaseConfig.jdbcUrl();
        String username = databaseConfig.username();
        String password = databaseConfig.password();

        if (isBlank(jdbcUrl) || isBlank(username) || password == null) {
            return Optional.empty();
        }

        int poolSize = parsePositiveInt(Integer.toString(databaseConfig.poolSize()));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);

        return Optional.of(new IpFlowMetricsRepository(new HikariDataSource(config)));
    }

    public void insertBatch(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {

            for (Row row : rows) {
                setNullableUnsignedInt(statement, 1, row.fromIPv4());
                setNullableVarBinary16(statement, 2, row.fromIPv6());
                setNullableUnsignedInt(statement, 3, row.toIPv4());
                setNullableVarBinary16(statement, 4, row.toIPv6());
                statement.setLong(5, row.byteSize());
                setNullableUnsignedInt(statement, 6, row.nextHopIPv4());
                setNullableVarBinary16(statement, 7, row.nextHopIPv6());
                statement.setInt(8, row.protocol());
                statement.setInt(9, row.srcPort());
                statement.setInt(10, row.dstPort());
                statement.setInt(11, row.pkts());
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException exception) {
            throw new RuntimeException("Konnte IPFlowMetrics nicht in MySQL schreiben", exception);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static void setNullableUnsignedInt(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }

        statement.setLong(index, value);
    }

    private static void setNullableVarBinary16(PreparedStatement statement, int index, byte[] value) throws SQLException {
        if (value == null || value.length != 16) {
            statement.setNull(index, Types.BINARY);
            return;
        }

        statement.setBytes(index, value);
    }

    private static int parsePositiveInt(String value) {
        if (isBlank(value)) {
            return 10;
        }

        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 10;
        } catch (NumberFormatException ignored) {
            return 10;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

