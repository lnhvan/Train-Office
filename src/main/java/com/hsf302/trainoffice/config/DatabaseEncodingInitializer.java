package com.hsf302.trainoffice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseEncodingInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseEncodingInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        convertColumnToNvarchar("passengers", "full_name", "NVARCHAR(100) NOT NULL");
        convertColumnToNvarchar("stations", "station_name", "NVARCHAR(120) NOT NULL");
        convertIndexedColumnToNvarchar(
                "stations",
                "city",
                "NVARCHAR(80) NOT NULL",
                "idx_stations_city"
        );
        convertColumnToNvarchar("routes", "route_name", "NVARCHAR(120) NOT NULL");
        convertColumnToNvarchar("trains", "train_name", "NVARCHAR(100) NOT NULL");
        convertColumnToNvarchar("trains", "train_type", "NVARCHAR(50) NOT NULL");
    }

    private void convertColumnToNvarchar(String tableName, String columnName, String definition) {
        List<String> dataTypes = jdbcTemplate.query("""
                SELECT DATA_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, (resultSet, rowNumber) -> resultSet.getString("DATA_TYPE"), tableName, columnName);

        if (!dataTypes.isEmpty() && !"nvarchar".equalsIgnoreCase(dataTypes.get(0))) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " " + definition);
            } catch (DataAccessException ignored) {
                // Keep startup alive; run the ALTER TABLE manually if the database has dependent constraints.
            }
        }
    }

    private void convertIndexedColumnToNvarchar(String tableName,
                                                String columnName,
                                                String definition,
                                                String indexName) {
        List<String> dataTypes = findColumnDataTypes(tableName, columnName);
        if (dataTypes.isEmpty() || "nvarchar".equalsIgnoreCase(dataTypes.get(0))) {
            return;
        }

        boolean indexExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM sys.indexes
                    WHERE name = ? AND object_id = OBJECT_ID(?)
                ) THEN 1 ELSE 0 END
                """, Boolean.class, indexName, tableName));

        if (indexExists) {
            jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName);
        }

        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " " + definition);
        } finally {
            if (indexExists) {
                jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
            }
        }
    }

    private List<String> findColumnDataTypes(String tableName, String columnName) {
        return jdbcTemplate.query("""
                SELECT DATA_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, (resultSet, rowNumber) -> resultSet.getString("DATA_TYPE"), tableName, columnName);
    }
}
