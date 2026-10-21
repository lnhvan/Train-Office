-- =====================================================================
-- Train-Office - Demo seed data for round-trip booking tests
-- Run this after Spring Boot/Hibernate has created the tables.
-- It can be run on top of Query.sql or on an empty schema.
--
-- What this adds:
--   1) Reverse routes:
--      - R003: SGN -> HAN
--      - R004: DAD -> HAN
--   2) Return trains with seats:
--      - SE2  for SGN -> HAN
--      - SE20 for DAD -> HAN
--   3) Scheduled return trips that match common round-trip searches:
--      - HAN -> SGN tomorrow, return SGN -> HAN in 3 days
--      - HAN -> DAD in 2 days, return DAD -> HAN in 4 days
--
-- The script is idempotent and safe to re-run.
-- =====================================================================

SET NOCOUNT ON;

-- ---------------------------------------------------------------------
-- Minimal base data bootstrap.
-- This makes the file usable even on an empty DB after ddl-auto=create-drop.
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='HAN') INSERT INTO stations (station_code, station_name, city) VALUES ('HAN', N'Ha Noi', N'Ha Noi');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='NDH') INSERT INTO stations (station_code, station_name, city) VALUES ('NDH', N'Nam Dinh', N'Nam Dinh');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='VIN') INSERT INTO stations (station_code, station_name, city) VALUES ('VIN', N'Vinh', N'Nghe An');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='DHO') INSERT INTO stations (station_code, station_name, city) VALUES ('DHO', N'Dong Hoi', N'Quang Binh');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='HUE') INSERT INTO stations (station_code, station_name, city) VALUES ('HUE', N'Hue', N'Thua Thien Hue');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='DAD') INSERT INTO stations (station_code, station_name, city) VALUES ('DAD', N'Da Nang', N'Da Nang');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='NTR') INSERT INTO stations (station_code, station_name, city) VALUES ('NTR', N'Nha Trang', N'Khanh Hoa');
IF NOT EXISTS (SELECT 1 FROM stations WHERE station_code='SGN') INSERT INTO stations (station_code, station_name, city) VALUES ('SGN', N'Sai Gon', N'TP. Ho Chi Minh');

IF NOT EXISTS (SELECT 1 FROM seat_types WHERE name=N'WINDOW') INSERT INTO seat_types (name, price_per_km) VALUES (N'WINDOW', 1.10);
IF NOT EXISTS (SELECT 1 FROM seat_types WHERE name=N'AISLE')  INSERT INTO seat_types (name, price_per_km) VALUES (N'AISLE', 1.00);
IF NOT EXISTS (SELECT 1 FROM seat_types WHERE name=N'MIDDLE') INSERT INTO seat_types (name, price_per_km) VALUES (N'MIDDLE', 0.95);

-- ---------------------------------------------------------------------
-- Required station ids from the base seed
-- ---------------------------------------------------------------------
DECLARE @rt_han_id BIGINT = (SELECT station_id FROM stations WHERE station_code='HAN');
DECLARE @rt_ndh_id BIGINT = (SELECT station_id FROM stations WHERE station_code='NDH');
DECLARE @rt_vin_id BIGINT = (SELECT station_id FROM stations WHERE station_code='VIN');
DECLARE @rt_dho_id BIGINT = (SELECT station_id FROM stations WHERE station_code='DHO');
DECLARE @rt_hue_id BIGINT = (SELECT station_id FROM stations WHERE station_code='HUE');
DECLARE @rt_dad_id BIGINT = (SELECT station_id FROM stations WHERE station_code='DAD');
DECLARE @rt_ntr_id BIGINT = (SELECT station_id FROM stations WHERE station_code='NTR');
DECLARE @rt_sgn_id BIGINT = (SELECT station_id FROM stations WHERE station_code='SGN');

-- ---------------------------------------------------------------------
-- Base routes, reverse routes, and route stations
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM routes WHERE route_code='R001')
    INSERT INTO routes (route_code, route_name, distance_km)
    VALUES ('R001', N'Ha Noi - Sai Gon', 1726);

IF NOT EXISTS (SELECT 1 FROM routes WHERE route_code='R002')
    INSERT INTO routes (route_code, route_name, distance_km)
    VALUES ('R002', N'Ha Noi - Da Nang', 791);

IF NOT EXISTS (SELECT 1 FROM routes WHERE route_code='R003')
    INSERT INTO routes (route_code, route_name, distance_km)
    VALUES ('R003', N'Sai Gon - Ha Noi', 1726);

IF NOT EXISTS (SELECT 1 FROM routes WHERE route_code='R004')
    INSERT INTO routes (route_code, route_name, distance_km)
    VALUES ('R004', N'Da Nang - Ha Noi', 791);

DECLARE @rt_route3_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R003');
DECLARE @rt_route4_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R004');
DECLARE @rt_boot_route1_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R001');
DECLARE @rt_boot_route2_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R002');

IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=1) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_han_id, 1);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=2) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_ndh_id, 2);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=3) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_vin_id, 3);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=4) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_dho_id, 4);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=5) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_hue_id, 5);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=6) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_dad_id, 6);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=7) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_ntr_id, 7);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route1_id AND station_order=8) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route1_id, @rt_sgn_id, 8);

IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=1) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_han_id, 1);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=2) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_ndh_id, 2);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=3) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_vin_id, 3);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=4) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_dho_id, 4);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=5) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_hue_id, 5);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_boot_route2_id AND station_order=6) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_boot_route2_id, @rt_dad_id, 6);

IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=1) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_sgn_id, 1);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=2) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_ntr_id, 2);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=3) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_dad_id, 3);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=4) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_hue_id, 4);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=5) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_dho_id, 5);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=6) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_vin_id, 6);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=7) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_ndh_id, 7);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route3_id AND station_order=8) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route3_id, @rt_han_id, 8);

IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=1) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_dad_id, 1);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=2) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_hue_id, 2);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=3) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_dho_id, 3);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=4) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_vin_id, 4);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=5) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_ndh_id, 5);
IF NOT EXISTS (SELECT 1 FROM route_stations WHERE route_id=@rt_route4_id AND station_order=6) INSERT INTO route_stations (route_id, station_id, station_order) VALUES (@rt_route4_id, @rt_han_id, 6);

-- ---------------------------------------------------------------------
-- Return trains + seats
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE1')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE1', N'SE1 - Ha Noi - Sai Gon', 'EXPRESS', 'AVAILABLE');

IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE3')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE3', N'SE3 - Ha Noi - Sai Gon', 'EXPRESS', 'AVAILABLE');

IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE19')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE19', N'SE19 - Ha Noi - Da Nang', 'REGIONAL', 'AVAILABLE');

IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE2')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE2', N'SE2 - Sai Gon - Ha Noi', 'EXPRESS', 'AVAILABLE');

IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE20')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE20', N'SE20 - Da Nang - Ha Noi', 'REGIONAL', 'AVAILABLE');

DECLARE @rt_train_id BIGINT;
DECLARE @rt_coach_id BIGINT;

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE1');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 15000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 10000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='B1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'B1', 'AISLE', 15000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='B2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'B2', 'WINDOW', 20000);

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE3');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 15000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 10000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C02') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C02', 'SLEEPER', 28);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C02');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='T1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'T1', 'WINDOW', 50000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='T2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'T2', 'AISLE', 45000);

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE19');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 18000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 13000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 9000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 18000);

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE2');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 15000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 10000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 20000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='B1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'B1', 'AISLE', 15000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='B2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'B2', 'WINDOW', 20000);

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE20');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 18000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 13000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 9000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 18000);

-- ---------------------------------------------------------------------
-- Scheduled return trips
-- ---------------------------------------------------------------------
DECLARE @rt_se2_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE2');
DECLARE @rt_se20_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE20');
DECLARE @rt_today DATETIME = CAST(CAST(GETDATE() AS date) AS datetime);

DECLARE @rt_depReturnSgnHan DATETIME = DATEADD(HOUR, 8, DATEADD(DAY, 3, @rt_today));
DECLARE @rt_depReturnDadHan DATETIME = DATEADD(HOUR, 14, DATEADD(DAY, 4, @rt_today));

IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se2_id AND route_id=@rt_route3_id AND departure_time=@rt_depReturnSgnHan)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status)
    VALUES (@rt_se2_id, @rt_route3_id, @rt_depReturnSgnHan, DATEADD(MINUTE, 1790, @rt_depReturnSgnHan), 895000, 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se20_id AND route_id=@rt_route4_id AND departure_time=@rt_depReturnDadHan)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status)
    VALUES (@rt_se20_id, @rt_route4_id, @rt_depReturnDadHan, DATEADD(MINUTE, 760, @rt_depReturnDadHan), 465000, 'SCHEDULED');

DECLARE @rt_trip_id BIGINT;

SET @rt_trip_id = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se2_id AND route_id=@rt_route3_id AND departure_time=@rt_depReturnSgnHan);
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=1) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_sgn_id, 1, NULL, DATEADD(MINUTE, 0, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=2) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_ntr_id, 2, DATEADD(MINUTE, 390, @rt_depReturnSgnHan), DATEADD(MINUTE, 395, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=3) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_dad_id, 3, DATEADD(MINUTE, 1030, @rt_depReturnSgnHan), DATEADD(MINUTE, 1035, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=4) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_hue_id, 4, DATEADD(MINUTE, 1095, @rt_depReturnSgnHan), DATEADD(MINUTE, 1100, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=5) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_dho_id, 5, DATEADD(MINUTE, 1260, @rt_depReturnSgnHan), DATEADD(MINUTE, 1265, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=6) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_vin_id, 6, DATEADD(MINUTE, 1470, @rt_depReturnSgnHan), DATEADD(MINUTE, 1475, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=7) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_ndh_id, 7, DATEADD(MINUTE, 1665, @rt_depReturnSgnHan), DATEADD(MINUTE, 1670, @rt_depReturnSgnHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=8) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_han_id, 8, DATEADD(MINUTE, 1790, @rt_depReturnSgnHan), NULL);

SET @rt_trip_id = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se20_id AND route_id=@rt_route4_id AND departure_time=@rt_depReturnDadHan);
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=1) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_dad_id, 1, NULL, DATEADD(MINUTE, 0, @rt_depReturnDadHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=2) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_hue_id, 2, DATEADD(MINUTE, 60, @rt_depReturnDadHan), DATEADD(MINUTE, 65, @rt_depReturnDadHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=3) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_dho_id, 3, DATEADD(MINUTE, 220, @rt_depReturnDadHan), DATEADD(MINUTE, 225, @rt_depReturnDadHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=4) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_vin_id, 4, DATEADD(MINUTE, 430, @rt_depReturnDadHan), DATEADD(MINUTE, 435, @rt_depReturnDadHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=5) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_ndh_id, 5, DATEADD(MINUTE, 630, @rt_depReturnDadHan), DATEADD(MINUTE, 635, @rt_depReturnDadHan));
IF NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_trip_id AND station_order=6) INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time) VALUES (@rt_trip_id, @rt_han_id, 6, DATEADD(MINUTE, 760, @rt_depReturnDadHan), NULL);

-- ---------------------------------------------------------------------
-- More demo accounts for manual testing.
-- Password is plain "123" to match the original seed style in Query.sql.
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.admin@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.admin@railjet.local', '123', N'Round Trip Admin', 'RT0000000001', '1988-01-15', 'MALE', 'ADMIN', 'ACTIVE', GETDATE(), GETDATE());

IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.customer1@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.customer1@railjet.local', '123', N'Round Trip Customer 1', 'RT0000000101', '1996-04-10', 'MALE', 'CUSTOMER', 'ACTIVE', GETDATE(), GETDATE());

IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.customer2@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.customer2@railjet.local', '123', N'Round Trip Customer 2', 'RT0000000102', '1997-05-20', 'FEMALE', 'CUSTOMER', 'ACTIVE', GETDATE(), GETDATE());

IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.customer3@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.customer3@railjet.local', '123', N'Round Trip Customer 3', 'RT0000000103', '1982-11-02', 'OTHER', 'CUSTOMER', 'ACTIVE', GETDATE(), GETDATE());

IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.locked@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.locked@railjet.local', '123', N'Locked Demo Customer', 'RT0000000199', '1990-09-09', 'FEMALE', 'CUSTOMER', 'LOCKED', GETDATE(), GETDATE());

IF NOT EXISTS (SELECT 1 FROM users WHERE email='rt.inactive@railjet.local')
    INSERT INTO users (email, password, full_name, identity_number, date_of_birth, gender, role, status, created_at, updated_at)
    VALUES ('rt.inactive@railjet.local', '123', N'Inactive Demo Customer', 'RT0000000198', '1991-10-10', 'MALE', 'CUSTOMER', 'INACTIVE', GETDATE(), GETDATE());

-- ---------------------------------------------------------------------
-- Extra return-side trains, coaches, and seats for denser seat testing.
-- ---------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE4')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE4', N'SE4 - Sai Gon - Ha Noi', 'EXPRESS', 'AVAILABLE');

IF NOT EXISTS (SELECT 1 FROM trains WHERE train_code='SE22')
    INSERT INTO trains (train_code, train_name, train_type, status)
    VALUES ('SE22', N'SE22 - Da Nang - Ha Noi', 'REGIONAL', 'AVAILABLE');

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE4');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 22000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 17000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 12000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 22000);
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C02') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C02', 'SLEEPER', 28);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C02');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='T1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'T1', 'WINDOW', 55000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='T2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'T2', 'AISLE', 50000);

SET @rt_train_id = (SELECT train_id FROM trains WHERE train_code='SE22');
IF NOT EXISTS (SELECT 1 FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01') INSERT INTO coaches (train_id, coach_number, coach_type, capacity) VALUES (@rt_train_id, 'C01', 'SOFT_SEAT', 56);
SET @rt_coach_id = (SELECT coach_id FROM coaches WHERE train_id=@rt_train_id AND coach_number='C01');
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A1') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A1', 'WINDOW', 19000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A2') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A2', 'AISLE', 14000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A3') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A3', 'MIDDLE', 10000);
IF NOT EXISTS (SELECT 1 FROM seats WHERE coach_id=@rt_coach_id AND seat_number='A4') INSERT INTO seats (coach_id, seat_number, seat_type, extra_price) VALUES (@rt_coach_id, 'A4', 'WINDOW', 19000);

-- ---------------------------------------------------------------------
-- Extra scheduled trips, so most future dates have multiple choices.
-- ---------------------------------------------------------------------
DECLARE @rt_se4_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE4');
DECLARE @rt_se22_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE22');
DECLARE @rt_route1_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R001');
DECLARE @rt_route2_id BIGINT = (SELECT route_id FROM routes WHERE route_code='R002');
DECLARE @rt_se1_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE1');
DECLARE @rt_se3_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE3');
DECLARE @rt_se19_id BIGINT = (SELECT train_id FROM trains WHERE train_code='SE19');

DECLARE @rt_depHanSgnD2 DATETIME = DATEADD(MINUTE, 450, DATEADD(DAY, 2, @rt_today));   -- +2 days 07:30
DECLARE @rt_depHanSgnD4 DATETIME = DATEADD(MINUTE, 1260, DATEADD(DAY, 4, @rt_today));  -- +4 days 21:00
DECLARE @rt_depHanSgnD5 DATETIME = DATEADD(MINUTE, 390, DATEADD(DAY, 5, @rt_today));   -- +5 days 06:30
DECLARE @rt_depSgnHanD2 DATETIME = DATEADD(MINUTE, 1320, DATEADD(DAY, 2, @rt_today));  -- +2 days 22:00
DECLARE @rt_depSgnHanD4 DATETIME = DATEADD(MINUTE, 540, DATEADD(DAY, 4, @rt_today));   -- +4 days 09:00
DECLARE @rt_depSgnHanD6 DATETIME = DATEADD(MINUTE, 1230, DATEADD(DAY, 6, @rt_today));  -- +6 days 20:30
DECLARE @rt_depHanDadD3 DATETIME = DATEADD(MINUTE, 510, DATEADD(DAY, 3, @rt_today));   -- +3 days 08:30
DECLARE @rt_depDadHanD3 DATETIME = DATEADD(MINUTE, 960, DATEADD(DAY, 3, @rt_today));   -- +3 days 16:00
DECLARE @rt_depDadHanD5 DATETIME = DATEADD(MINUTE, 420, DATEADD(DAY, 5, @rt_today));   -- +5 days 07:00

IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se1_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD2)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se1_id, @rt_route1_id, @rt_depHanSgnD2, DATEADD(MINUTE, 1790, @rt_depHanSgnD2), 910000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se3_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD4)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se3_id, @rt_route1_id, @rt_depHanSgnD4, DATEADD(MINUTE, 1790, @rt_depHanSgnD4), 930000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se1_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD5)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se1_id, @rt_route1_id, @rt_depHanSgnD5, DATEADD(MINUTE, 1790, @rt_depHanSgnD5), 940000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se4_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD2)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se4_id, @rt_route3_id, @rt_depSgnHanD2, DATEADD(MINUTE, 1790, @rt_depSgnHanD2), 905000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se2_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD4)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se2_id, @rt_route3_id, @rt_depSgnHanD4, DATEADD(MINUTE, 1790, @rt_depSgnHanD4), 915000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se4_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD6)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se4_id, @rt_route3_id, @rt_depSgnHanD6, DATEADD(MINUTE, 1790, @rt_depSgnHanD6), 925000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se19_id AND route_id=@rt_route2_id AND departure_time=@rt_depHanDadD3)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se19_id, @rt_route2_id, @rt_depHanDadD3, DATEADD(MINUTE, 760, @rt_depHanDadD3), 475000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se22_id AND route_id=@rt_route4_id AND departure_time=@rt_depDadHanD3)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se22_id, @rt_route4_id, @rt_depDadHanD3, DATEADD(MINUTE, 760, @rt_depDadHanD3), 472000, 'SCHEDULED');
IF NOT EXISTS (SELECT 1 FROM train_trips WHERE train_id=@rt_se20_id AND route_id=@rt_route4_id AND departure_time=@rt_depDadHanD5)
    INSERT INTO train_trips (train_id, route_id, departure_time, arrival_time, base_price, status) VALUES (@rt_se20_id, @rt_route4_id, @rt_depDadHanD5, DATEADD(MINUTE, 760, @rt_depDadHanD5), 482000, 'SCHEDULED');

-- Add trip stations for the extra trips with set-based idempotent inserts.
DECLARE @rt_extra_trip BIGINT;

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se1_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD2);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_han_id, 1, NULL, DATEADD(MINUTE,0,@rt_depHanSgnD2)),
(@rt_ndh_id, 2, DATEADD(MINUTE,130,@rt_depHanSgnD2), DATEADD(MINUTE,135,@rt_depHanSgnD2)),
(@rt_vin_id, 3, DATEADD(MINUTE,330,@rt_depHanSgnD2), DATEADD(MINUTE,335,@rt_depHanSgnD2)),
(@rt_dho_id, 4, DATEADD(MINUTE,540,@rt_depHanSgnD2), DATEADD(MINUTE,545,@rt_depHanSgnD2)),
(@rt_hue_id, 5, DATEADD(MINUTE,700,@rt_depHanSgnD2), DATEADD(MINUTE,705,@rt_depHanSgnD2)),
(@rt_dad_id, 6, DATEADD(MINUTE,760,@rt_depHanSgnD2), DATEADD(MINUTE,765,@rt_depHanSgnD2)),
(@rt_ntr_id, 7, DATEADD(MINUTE,1400,@rt_depHanSgnD2), DATEADD(MINUTE,1405,@rt_depHanSgnD2)),
(@rt_sgn_id, 8, DATEADD(MINUTE,1790,@rt_depHanSgnD2), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se3_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD4);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_han_id, 1, NULL, DATEADD(MINUTE,0,@rt_depHanSgnD4)),
(@rt_ndh_id, 2, DATEADD(MINUTE,130,@rt_depHanSgnD4), DATEADD(MINUTE,135,@rt_depHanSgnD4)),
(@rt_vin_id, 3, DATEADD(MINUTE,330,@rt_depHanSgnD4), DATEADD(MINUTE,335,@rt_depHanSgnD4)),
(@rt_dho_id, 4, DATEADD(MINUTE,540,@rt_depHanSgnD4), DATEADD(MINUTE,545,@rt_depHanSgnD4)),
(@rt_hue_id, 5, DATEADD(MINUTE,700,@rt_depHanSgnD4), DATEADD(MINUTE,705,@rt_depHanSgnD4)),
(@rt_dad_id, 6, DATEADD(MINUTE,760,@rt_depHanSgnD4), DATEADD(MINUTE,765,@rt_depHanSgnD4)),
(@rt_ntr_id, 7, DATEADD(MINUTE,1400,@rt_depHanSgnD4), DATEADD(MINUTE,1405,@rt_depHanSgnD4)),
(@rt_sgn_id, 8, DATEADD(MINUTE,1790,@rt_depHanSgnD4), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se1_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD5);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_han_id, 1, NULL, DATEADD(MINUTE,0,@rt_depHanSgnD5)),
(@rt_ndh_id, 2, DATEADD(MINUTE,130,@rt_depHanSgnD5), DATEADD(MINUTE,135,@rt_depHanSgnD5)),
(@rt_vin_id, 3, DATEADD(MINUTE,330,@rt_depHanSgnD5), DATEADD(MINUTE,335,@rt_depHanSgnD5)),
(@rt_dho_id, 4, DATEADD(MINUTE,540,@rt_depHanSgnD5), DATEADD(MINUTE,545,@rt_depHanSgnD5)),
(@rt_hue_id, 5, DATEADD(MINUTE,700,@rt_depHanSgnD5), DATEADD(MINUTE,705,@rt_depHanSgnD5)),
(@rt_dad_id, 6, DATEADD(MINUTE,760,@rt_depHanSgnD5), DATEADD(MINUTE,765,@rt_depHanSgnD5)),
(@rt_ntr_id, 7, DATEADD(MINUTE,1400,@rt_depHanSgnD5), DATEADD(MINUTE,1405,@rt_depHanSgnD5)),
(@rt_sgn_id, 8, DATEADD(MINUTE,1790,@rt_depHanSgnD5), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se4_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD2);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_sgn_id, 1, NULL, DATEADD(MINUTE,0,@rt_depSgnHanD2)),
(@rt_ntr_id, 2, DATEADD(MINUTE,390,@rt_depSgnHanD2), DATEADD(MINUTE,395,@rt_depSgnHanD2)),
(@rt_dad_id, 3, DATEADD(MINUTE,1030,@rt_depSgnHanD2), DATEADD(MINUTE,1035,@rt_depSgnHanD2)),
(@rt_hue_id, 4, DATEADD(MINUTE,1095,@rt_depSgnHanD2), DATEADD(MINUTE,1100,@rt_depSgnHanD2)),
(@rt_dho_id, 5, DATEADD(MINUTE,1260,@rt_depSgnHanD2), DATEADD(MINUTE,1265,@rt_depSgnHanD2)),
(@rt_vin_id, 6, DATEADD(MINUTE,1470,@rt_depSgnHanD2), DATEADD(MINUTE,1475,@rt_depSgnHanD2)),
(@rt_ndh_id, 7, DATEADD(MINUTE,1665,@rt_depSgnHanD2), DATEADD(MINUTE,1670,@rt_depSgnHanD2)),
(@rt_han_id, 8, DATEADD(MINUTE,1790,@rt_depSgnHanD2), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se2_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD4);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_sgn_id, 1, NULL, DATEADD(MINUTE,0,@rt_depSgnHanD4)),
(@rt_ntr_id, 2, DATEADD(MINUTE,390,@rt_depSgnHanD4), DATEADD(MINUTE,395,@rt_depSgnHanD4)),
(@rt_dad_id, 3, DATEADD(MINUTE,1030,@rt_depSgnHanD4), DATEADD(MINUTE,1035,@rt_depSgnHanD4)),
(@rt_hue_id, 4, DATEADD(MINUTE,1095,@rt_depSgnHanD4), DATEADD(MINUTE,1100,@rt_depSgnHanD4)),
(@rt_dho_id, 5, DATEADD(MINUTE,1260,@rt_depSgnHanD4), DATEADD(MINUTE,1265,@rt_depSgnHanD4)),
(@rt_vin_id, 6, DATEADD(MINUTE,1470,@rt_depSgnHanD4), DATEADD(MINUTE,1475,@rt_depSgnHanD4)),
(@rt_ndh_id, 7, DATEADD(MINUTE,1665,@rt_depSgnHanD4), DATEADD(MINUTE,1670,@rt_depSgnHanD4)),
(@rt_han_id, 8, DATEADD(MINUTE,1790,@rt_depSgnHanD4), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se4_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD6);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_sgn_id, 1, NULL, DATEADD(MINUTE,0,@rt_depSgnHanD6)),
(@rt_ntr_id, 2, DATEADD(MINUTE,390,@rt_depSgnHanD6), DATEADD(MINUTE,395,@rt_depSgnHanD6)),
(@rt_dad_id, 3, DATEADD(MINUTE,1030,@rt_depSgnHanD6), DATEADD(MINUTE,1035,@rt_depSgnHanD6)),
(@rt_hue_id, 4, DATEADD(MINUTE,1095,@rt_depSgnHanD6), DATEADD(MINUTE,1100,@rt_depSgnHanD6)),
(@rt_dho_id, 5, DATEADD(MINUTE,1260,@rt_depSgnHanD6), DATEADD(MINUTE,1265,@rt_depSgnHanD6)),
(@rt_vin_id, 6, DATEADD(MINUTE,1470,@rt_depSgnHanD6), DATEADD(MINUTE,1475,@rt_depSgnHanD6)),
(@rt_ndh_id, 7, DATEADD(MINUTE,1665,@rt_depSgnHanD6), DATEADD(MINUTE,1670,@rt_depSgnHanD6)),
(@rt_han_id, 8, DATEADD(MINUTE,1790,@rt_depSgnHanD6), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se19_id AND route_id=@rt_route2_id AND departure_time=@rt_depHanDadD3);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_han_id, 1, NULL, DATEADD(MINUTE,0,@rt_depHanDadD3)),
(@rt_ndh_id, 2, DATEADD(MINUTE,130,@rt_depHanDadD3), DATEADD(MINUTE,135,@rt_depHanDadD3)),
(@rt_vin_id, 3, DATEADD(MINUTE,330,@rt_depHanDadD3), DATEADD(MINUTE,335,@rt_depHanDadD3)),
(@rt_dho_id, 4, DATEADD(MINUTE,540,@rt_depHanDadD3), DATEADD(MINUTE,545,@rt_depHanDadD3)),
(@rt_hue_id, 5, DATEADD(MINUTE,700,@rt_depHanDadD3), DATEADD(MINUTE,705,@rt_depHanDadD3)),
(@rt_dad_id, 6, DATEADD(MINUTE,760,@rt_depHanDadD3), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se22_id AND route_id=@rt_route4_id AND departure_time=@rt_depDadHanD3);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_dad_id, 1, NULL, DATEADD(MINUTE,0,@rt_depDadHanD3)),
(@rt_hue_id, 2, DATEADD(MINUTE,60,@rt_depDadHanD3), DATEADD(MINUTE,65,@rt_depDadHanD3)),
(@rt_dho_id, 3, DATEADD(MINUTE,220,@rt_depDadHanD3), DATEADD(MINUTE,225,@rt_depDadHanD3)),
(@rt_vin_id, 4, DATEADD(MINUTE,430,@rt_depDadHanD3), DATEADD(MINUTE,435,@rt_depDadHanD3)),
(@rt_ndh_id, 5, DATEADD(MINUTE,630,@rt_depDadHanD3), DATEADD(MINUTE,635,@rt_depDadHanD3)),
(@rt_han_id, 6, DATEADD(MINUTE,760,@rt_depDadHanD3), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

SET @rt_extra_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se20_id AND route_id=@rt_route4_id AND departure_time=@rt_depDadHanD5);
INSERT INTO trip_stations (trip_id, station_id, station_order, arrival_time, departure_time)
SELECT @rt_extra_trip, station_id, station_order, arrival_time, departure_time
FROM (VALUES
(@rt_dad_id, 1, NULL, DATEADD(MINUTE,0,@rt_depDadHanD5)),
(@rt_hue_id, 2, DATEADD(MINUTE,60,@rt_depDadHanD5), DATEADD(MINUTE,65,@rt_depDadHanD5)),
(@rt_dho_id, 3, DATEADD(MINUTE,220,@rt_depDadHanD5), DATEADD(MINUTE,225,@rt_depDadHanD5)),
(@rt_vin_id, 4, DATEADD(MINUTE,430,@rt_depDadHanD5), DATEADD(MINUTE,435,@rt_depDadHanD5)),
(@rt_ndh_id, 5, DATEADD(MINUTE,630,@rt_depDadHanD5), DATEADD(MINUTE,635,@rt_depDadHanD5)),
(@rt_han_id, 6, DATEADD(MINUTE,760,@rt_depDadHanD5), NULL)
) v(station_id, station_order, arrival_time, departure_time)
WHERE NOT EXISTS (SELECT 1 FROM trip_stations WHERE trip_id=@rt_extra_trip AND station_order=v.station_order);

-- ---------------------------------------------------------------------
-- Demo bookings that block some seats and cover booking/payment/refund states.
-- ---------------------------------------------------------------------
DECLARE @rt_customer1 BIGINT = (SELECT user_id FROM users WHERE email='rt.customer1@railjet.local');
DECLARE @rt_customer2 BIGINT = (SELECT user_id FROM users WHERE email='rt.customer2@railjet.local');
DECLARE @rt_customer3 BIGINT = (SELECT user_id FROM users WHERE email='rt.customer3@railjet.local');
DECLARE @rt_booking_id BIGINT;
DECLARE @rt_passenger_id BIGINT;
DECLARE @rt_demo_trip BIGINT;
DECLARE @rt_demo_seat1 BIGINT;
DECLARE @rt_demo_seat2 BIGINT;

SET @rt_demo_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se2_id AND route_id=@rt_route3_id AND departure_time=@rt_depReturnSgnHan);
SET @rt_demo_seat1 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se2_id AND coach_number='C01') AND seat_number='A1');
SET @rt_demo_seat2 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se2_id AND coach_number='C01') AND seat_number='A2');
IF NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code='RTBK0001')
    INSERT INTO bookings (booking_code, trip_id, departure_station_id, arrival_station_id, user_id, booker_name, booker_phone, booker_email, total_amount, booking_status, created_at)
    VALUES ('RTBK0001', @rt_demo_trip, @rt_sgn_id, @rt_han_id, @rt_customer1, N'Round Trip Customer 1', '0988000001', 'rt.customer1@railjet.local', 1825000, 'PAID', DATEADD(DAY,-1,GETDATE()));
SET @rt_booking_id = (SELECT booking_id FROM bookings WHERE booking_code='RTBK0001');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0001')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Adult Demo Passenger', 'RTPAX0001', '1992-02-02', 'MALE', 'ADULT', N'Self');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0001');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0001')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0001', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat1, 915000, 'CONFIRMED');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0002')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Child Demo Passenger', 'RTPAX0002', '2016-06-12', 'FEMALE', 'CHILD', N'Child');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0002');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0002')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0002', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat2, 910000, 'CONFIRMED');
IF NOT EXISTS (SELECT 1 FROM payments WHERE transaction_code='RTPAY0001')
    INSERT INTO payments (booking_id, payment_method, amount, transaction_code, otp_code, payment_status, paid_at)
    VALUES (@rt_booking_id, 'VNPAY', 1825000, 'RTPAY0001', '654321', 'SUCCESS', GETDATE());
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code='RTINV0001')
    INSERT INTO invoices (invoice_code, payment_id, total_amount, issued_at)
    VALUES ('RTINV0001', (SELECT payment_id FROM payments WHERE transaction_code='RTPAY0001'), 1825000, GETDATE());

SET @rt_demo_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se4_id AND route_id=@rt_route3_id AND departure_time=@rt_depSgnHanD2);
SET @rt_demo_seat1 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se4_id AND coach_number='C01') AND seat_number='A1');
IF NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code='RTBK0002')
    INSERT INTO bookings (booking_code, trip_id, departure_station_id, arrival_station_id, user_id, booker_name, booker_phone, booker_email, total_amount, booking_status, created_at)
    VALUES ('RTBK0002', @rt_demo_trip, @rt_sgn_id, @rt_han_id, @rt_customer2, N'Round Trip Customer 2', '0988000002', 'rt.customer2@railjet.local', 927000, 'PENDING_PAYMENT', GETDATE());
SET @rt_booking_id = (SELECT booking_id FROM bookings WHERE booking_code='RTBK0002');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0003')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Pending Hold Passenger', 'RTPAX0003', '1994-03-03', 'FEMALE', 'ADULT', N'Self');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0003');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0003')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0003', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat1, 927000, 'BOOKED');
IF NOT EXISTS (SELECT 1 FROM payments WHERE transaction_code='RTPAY0002')
    INSERT INTO payments (booking_id, payment_method, amount, transaction_code, otp_code, payment_status, paid_at)
    VALUES (@rt_booking_id, 'BANK_TRANSFER', 927000, 'RTPAY0002', NULL, 'PENDING', NULL);

SET @rt_demo_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se20_id AND route_id=@rt_route4_id AND departure_time=@rt_depReturnDadHan);
SET @rt_demo_seat1 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se20_id AND coach_number='C01') AND seat_number='A1');
IF NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code='RTBK0003')
    INSERT INTO bookings (booking_code, trip_id, departure_station_id, arrival_station_id, user_id, booker_name, booker_phone, booker_email, total_amount, booking_status, created_at)
    VALUES ('RTBK0003', @rt_demo_trip, @rt_dad_id, @rt_han_id, @rt_customer3, N'Round Trip Customer 3', '0988000003', 'rt.customer3@railjet.local', 483000, 'REFUNDED', DATEADD(DAY,-2,GETDATE()));
SET @rt_booking_id = (SELECT booking_id FROM bookings WHERE booking_code='RTBK0003');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0004')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Refunded Demo Passenger', 'RTPAX0004', '1980-08-08', 'OTHER', 'SENIOR', N'Self');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0004');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0004')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0004', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat1, 483000, 'REFUNDED');
IF NOT EXISTS (SELECT 1 FROM payments WHERE transaction_code='RTPAY0003')
    INSERT INTO payments (booking_id, payment_method, amount, transaction_code, otp_code, payment_status, paid_at)
    VALUES (@rt_booking_id, 'CASH', 483000, 'RTPAY0003', NULL, 'SUCCESS', DATEADD(DAY,-2,GETDATE()));
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code='RTINV0003')
    INSERT INTO invoices (invoice_code, payment_id, total_amount, issued_at)
    VALUES ('RTINV0003', (SELECT payment_id FROM payments WHERE transaction_code='RTPAY0003'), 483000, DATEADD(DAY,-2,GETDATE()));
IF NOT EXISTS (SELECT 1 FROM refunds WHERE refund_code='RTRF0003')
    INSERT INTO refunds (refund_code, ticket_id, refund_amount, refund_reason, refund_status, refunded_at)
    VALUES ('RTRF0003', (SELECT ticket_id FROM tickets WHERE ticket_code='RTTK0004'), 483000, N'Demo refund for round-trip testing', 'COMPLETED', GETDATE());

SET @rt_demo_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se22_id AND route_id=@rt_route4_id AND departure_time=@rt_depDadHanD3);
SET @rt_demo_seat1 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se22_id AND coach_number='C01') AND seat_number='A2');
IF NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code='RTBK0004')
    INSERT INTO bookings (booking_code, trip_id, departure_station_id, arrival_station_id, user_id, booker_name, booker_phone, booker_email, total_amount, booking_status, created_at)
    VALUES ('RTBK0004', @rt_demo_trip, @rt_dad_id, @rt_han_id, @rt_customer1, N'Round Trip Customer 1', '0988000001', 'rt.customer1@railjet.local', 486000, 'CANCELLED', GETDATE());
SET @rt_booking_id = (SELECT booking_id FROM bookings WHERE booking_code='RTBK0004');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0005')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Cancelled Demo Passenger', 'RTPAX0005', '2001-01-01', 'MALE', 'ADULT', N'Friend');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0005');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0005')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0005', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat1, 486000, 'CANCELLED');
IF NOT EXISTS (SELECT 1 FROM payments WHERE transaction_code='RTPAY0004')
    INSERT INTO payments (booking_id, payment_method, amount, transaction_code, otp_code, payment_status, paid_at)
    VALUES (@rt_booking_id, 'BANK_TRANSFER', 486000, 'RTPAY0004', NULL, 'FAILED', NULL);

SET @rt_demo_trip = (SELECT trip_id FROM train_trips WHERE train_id=@rt_se1_id AND route_id=@rt_route1_id AND departure_time=@rt_depHanSgnD2);
SET @rt_demo_seat1 = (SELECT seat_id FROM seats WHERE coach_id=(SELECT coach_id FROM coaches WHERE train_id=@rt_se1_id AND coach_number='C01') AND seat_number='B1');
IF NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code='RTBK0005')
    INSERT INTO bookings (booking_code, trip_id, departure_station_id, arrival_station_id, user_id, booker_name, booker_phone, booker_email, total_amount, booking_status, created_at)
    VALUES ('RTBK0005', @rt_demo_trip, @rt_han_id, @rt_sgn_id, NULL, N'Guest Demo Booker', '0988999000', 'rt.guest@railjet.local', 925000, 'PAID', GETDATE());
SET @rt_booking_id = (SELECT booking_id FROM bookings WHERE booking_code='RTBK0005');
IF NOT EXISTS (SELECT 1 FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0006')
    INSERT INTO passengers (booking_id, full_name, identity_number, date_of_birth, gender, passenger_type, relationship_to_booker)
    VALUES (@rt_booking_id, N'Guest Demo Passenger', 'RTPAX0006', '1995-12-12', 'FEMALE', 'ADULT', N'Self');
SET @rt_passenger_id = (SELECT passenger_id FROM passengers WHERE booking_id=@rt_booking_id AND identity_number='RTPAX0006');
IF NOT EXISTS (SELECT 1 FROM tickets WHERE ticket_code='RTTK0006')
    INSERT INTO tickets (ticket_code, booking_id, passenger_id, trip_id, seat_id, ticket_price, ticket_status)
    VALUES ('RTTK0006', @rt_booking_id, @rt_passenger_id, @rt_demo_trip, @rt_demo_seat1, 925000, 'CONFIRMED');
IF NOT EXISTS (SELECT 1 FROM payments WHERE transaction_code='RTPAY0005')
    INSERT INTO payments (booking_id, payment_method, amount, transaction_code, otp_code, payment_status, paid_at)
    VALUES (@rt_booking_id, 'VNPAY', 925000, 'RTPAY0005', '123456', 'SUCCESS', GETDATE());
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code='RTINV0005')
    INSERT INTO invoices (invoice_code, payment_id, total_amount, issued_at)
    VALUES ('RTINV0005', (SELECT payment_id FROM payments WHERE transaction_code='RTPAY0005'), 925000, GETDATE());

-- ---------------------------------------------------------------------
-- Quick checks for the exact round-trip searches.
-- Each row should return trip_count >= 1 after Query.sql + this file.
-- ---------------------------------------------------------------------
SELECT 'OUTBOUND: HAN -> SGN, tomorrow' AS test_case,
       COUNT(*) AS trip_count
FROM train_trips tt
JOIN trip_stations dep ON dep.trip_id = tt.trip_id AND dep.station_id = @rt_han_id
JOIN trip_stations arr ON arr.trip_id = tt.trip_id AND arr.station_id = @rt_sgn_id
WHERE tt.status = 'SCHEDULED'
  AND tt.departure_time >= DATEADD(DAY, 1, @rt_today)
  AND tt.departure_time < DATEADD(DAY, 2, @rt_today)
  AND dep.station_order < arr.station_order
UNION ALL
SELECT 'RETURN: SGN -> HAN, +3 days',
       COUNT(*)
FROM train_trips tt
JOIN trip_stations dep ON dep.trip_id = tt.trip_id AND dep.station_id = @rt_sgn_id
JOIN trip_stations arr ON arr.trip_id = tt.trip_id AND arr.station_id = @rt_han_id
WHERE tt.status = 'SCHEDULED'
  AND tt.departure_time >= DATEADD(DAY, 3, @rt_today)
  AND tt.departure_time < DATEADD(DAY, 4, @rt_today)
  AND dep.station_order < arr.station_order
UNION ALL
SELECT 'OUTBOUND: HAN -> DAD, +2 days',
       COUNT(*)
FROM train_trips tt
JOIN trip_stations dep ON dep.trip_id = tt.trip_id AND dep.station_id = @rt_han_id
JOIN trip_stations arr ON arr.trip_id = tt.trip_id AND arr.station_id = @rt_dad_id
WHERE tt.status = 'SCHEDULED'
  AND tt.departure_time >= DATEADD(DAY, 2, @rt_today)
  AND tt.departure_time < DATEADD(DAY, 3, @rt_today)
  AND dep.station_order < arr.station_order
UNION ALL
SELECT 'RETURN: DAD -> HAN, +4 days',
       COUNT(*)
FROM train_trips tt
JOIN trip_stations dep ON dep.trip_id = tt.trip_id AND dep.station_id = @rt_dad_id
JOIN trip_stations arr ON arr.trip_id = tt.trip_id AND arr.station_id = @rt_han_id
WHERE tt.status = 'SCHEDULED'
  AND tt.departure_time >= DATEADD(DAY, 4, @rt_today)
  AND tt.departure_time < DATEADD(DAY, 5, @rt_today)
  AND dep.station_order < arr.station_order;

SELECT 'rt_users' AS demo_table, COUNT(*) AS rows FROM users WHERE email LIKE 'rt.%@railjet.local'
UNION ALL SELECT 'rt_routes', COUNT(*) FROM routes WHERE route_code IN ('R003','R004')
UNION ALL SELECT 'rt_trains', COUNT(*) FROM trains WHERE train_code IN ('SE2','SE4','SE20','SE22')
UNION ALL SELECT 'rt_train_trips', COUNT(*) FROM train_trips WHERE train_id IN (SELECT train_id FROM trains WHERE train_code IN ('SE2','SE4','SE20','SE22'))
UNION ALL SELECT 'rt_bookings', COUNT(*) FROM bookings WHERE booking_code LIKE 'RTBK%'
UNION ALL SELECT 'rt_passengers', COUNT(*) FROM passengers WHERE identity_number LIKE 'RTPAX%'
UNION ALL SELECT 'rt_tickets', COUNT(*) FROM tickets WHERE ticket_code LIKE 'RTTK%'
UNION ALL SELECT 'rt_payments', COUNT(*) FROM payments WHERE transaction_code LIKE 'RTPAY%'
UNION ALL SELECT 'rt_invoices', COUNT(*) FROM invoices WHERE invoice_code LIKE 'RTINV%'
UNION ALL SELECT 'rt_refunds', COUNT(*) FROM refunds WHERE refund_code LIKE 'RTRF%';
