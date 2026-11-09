package com.hsf302.trainoffice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Map;

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
        try {
            jdbcTemplate.update("UPDATE trains SET status = 'AVAILABLE' WHERE status = 'ON_TRIP'");
        } catch (DataAccessException ignored) {
        }
                backfillBookingTimes();
        updateTripStatusCheckConstraint();
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

    private void updateTripStatusCheckConstraint() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT cc.name, cc.definition
                    FROM sys.check_constraints cc
                    JOIN sys.tables t ON cc.parent_object_id = t.object_id
                    WHERE t.name = 'train_trips'
                      AND (cc.definition LIKE '%status%' OR cc.name LIKE '%statu%')
                    """);

            boolean needsNewConstraint = false;
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("name");
                String def = (String) row.get("definition");
                if (def == null || !def.contains("ONGOING")) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE train_trips DROP CONSTRAINT [" + name + "]");
                        needsNewConstraint = true;
                    } catch (DataAccessException ignored) {
                    }
                }
            }

            if (needsNewConstraint || rows.isEmpty()) {
                try {
                    jdbcTemplate.execute("""
                            ALTER TABLE train_trips
                            ADD CONSTRAINT CK_train_trips_status
                            CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'))
                            """);
                } catch (DataAccessException ignored) {
                }
            }
        } catch (DataAccessException ignored) {
            // Non-SQL-Server DB or sys catalog not available
        }
    }


    private void backfillBookingTimes() {
        try {
            jdbcTemplate.update("""
                UPDATE b
                SET b.departure_time = COALESCE(dep_ts.departure_time, dep_ts.arrival_time, tt.departure_time),
                    b.arrival_time = COALESCE(arr_ts.arrival_time, arr_ts.departure_time, tt.arrival_time)
                FROM bookings b
                JOIN train_trips tt ON b.trip_id = tt.trip_id
                LEFT JOIN trip_stations dep_ts ON dep_ts.trip_id = b.trip_id
                    AND dep_ts.route_station_id IN (SELECT route_station_id FROM route_stations WHERE station_id = b.departure_station_id)
                LEFT JOIN trip_stations arr_ts ON arr_ts.trip_id = b.trip_id
                    AND arr_ts.route_station_id IN (SELECT route_station_id FROM route_stations WHERE station_id = b.arrival_station_id)
                WHERE b.departure_time IS NULL OR b.arrival_time IS NULL
            """);
        } catch (Exception ignored) {
        }
    }
}
