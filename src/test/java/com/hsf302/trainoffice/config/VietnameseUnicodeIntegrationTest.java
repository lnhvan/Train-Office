package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class VietnameseUnicodeIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private DatabaseEncodingInitializer databaseEncodingInitializer;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    void requiredTextColumnsAreNvarcharAndInitializerIsIdempotent() throws Exception {
        databaseEncodingInitializer.run(new DefaultApplicationArguments(new String[0]));
        databaseEncodingInitializer.run(new DefaultApplicationArguments(new String[0]));

        Map<String, String> types = jdbcTemplate.query("""
                        SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE (TABLE_NAME = 'stations' AND COLUMN_NAME IN ('station_name', 'city'))
                           OR (TABLE_NAME = 'routes' AND COLUMN_NAME = 'route_name')
                           OR (TABLE_NAME = 'trains' AND COLUMN_NAME IN ('train_name', 'train_type'))
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("TABLE_NAME") + "." + resultSet.getString("COLUMN_NAME"),
                                resultSet.getString("DATA_TYPE"));
                    }
                    return result;
                });

        assertEquals(5, types.size());
        assertTrue(types.values().stream().allMatch("nvarchar"::equalsIgnoreCase));
    }

    @Test
    void stationCreatedInVietnameseSurvivesDatabaseRoundTrip() {
        Station station = stationRepository.findByStationCode("UTF-TST")
                .orElseGet(() -> Station.builder().stationCode("UTF-TST").build());
        station.setStationName("Ga thử nghiệm Đắk Lắk");
        station.setCity("Thành phố Hồ Chí Minh");
        stationRepository.saveAndFlush(station);

        Station reloaded = jdbcTemplate.queryForObject("""
                        SELECT station_id, station_code, station_name, city
                        FROM stations WHERE station_code = 'UTF-TST'
                        """,
                (resultSet, rowNumber) -> Station.builder()
                        .stationId(resultSet.getLong("station_id"))
                        .stationCode(resultSet.getString("station_code"))
                        .stationName(resultSet.getString("station_name"))
                        .city(resultSet.getString("city"))
                        .build());

        assertNotNull(reloaded);
        assertEquals("Ga thử nghiệm Đắk Lắk", reloaded.getStationName());
        assertEquals("Thành phố Hồ Chí Minh", reloaded.getCity());
    }

    @Test
    void startupRepairsCorruptedSeedByBusinessKeyWithoutDuplicates() throws Exception {
        Station haNoi = stationRepository.findByStationCode("HAN").orElseThrow();
        haNoi.setStationName("HÃ  Ná»™i");
        haNoi.setCity("HÃ  Ná»™i");
        stationRepository.saveAndFlush(haNoi);
        long countBefore = stationRepository.count();

        dataInitializer.run();

        Station repaired = stationRepository.findByStationCode("HAN").orElseThrow();
        assertEquals("Hà Nội", repaired.getStationName());
        assertEquals("Hà Nội", repaired.getCity());
        assertEquals(countBefore, stationRepository.count());

        Map<String, String> expectedCities = Map.of(
                "DNG", "Đà Nẵng",
                "HCM", "Thành phố Hồ Chí Minh");
        Map<String, String> actualCities = stationRepository.findAll().stream()
                .filter(station -> expectedCities.containsKey(station.getStationCode()))
                .collect(Collectors.toMap(Station::getStationCode, Station::getCity));
        assertEquals(expectedCities, actualCities);
    }
}
