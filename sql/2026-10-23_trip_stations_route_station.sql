/* =====================================================================
   Train-Office — chuẩn hóa bảng trip_stations
   ---------------------------------------------------------------------
   Trước: trip_stations(trip_id, station_id, station_order, arrival_time, departure_time)
   Sau:   trip_stations(trip_id, route_station_id, arrival_time, departure_time)
          ga + thứ tự dừng đọc qua route_stations.

   Cách chạy (SQL Server, database train_office):
     1. TẮT ứng dụng và sao lưu database.
     2. Chạy lần lượt từng lệnh bên dưới (DataGrip / IntelliJ: chọn hết rồi Execute,
        SSMS: F5). Mỗi bước là MỘT lệnh độc lập nên công cụ tách theo dấu ";" vẫn chạy đúng.
     3. Nếu bước 3 báo lỗi: có điểm dừng mà ga không nằm trên tuyến của chuyến.
        Chạy câu SELECT ở bước 3b để xem, xử lý xong rồi chạy lại từ đầu.
     4. Khởi động lại ứng dụng với code mới.

   Chạy lại nhiều lần vẫn an toàn.
   ===================================================================== */

USE train_office;

/* 1. Thêm cột route_station_id nếu chưa có */
IF COL_LENGTH('dbo.trip_stations', 'route_station_id') IS NULL
    EXEC(N'ALTER TABLE dbo.trip_stations ADD route_station_id BIGINT NULL');

/* 2. Gắn route_station_id theo (tuyến của chuyến, ga) */
IF COL_LENGTH('dbo.trip_stations', 'station_id') IS NOT NULL
    EXEC(N'UPDATE ts
           SET ts.route_station_id = rs.route_station_id
           FROM dbo.trip_stations ts
                JOIN dbo.train_trips t     ON t.trip_id = ts.trip_id
                JOIN dbo.route_stations rs ON rs.route_id = t.route_id AND rs.station_id = ts.station_id
           WHERE ts.route_station_id IS NULL');

/* 3. Dừng nếu còn điểm dừng không gắn được (ga không nằm trên tuyến của chuyến) */
IF EXISTS (SELECT 1 FROM dbo.trip_stations WHERE route_station_id IS NULL)
    THROW 50001, N'Có điểm dừng mà ga không nằm trên tuyến của chuyến. Chạy câu SELECT ở bước 3b để xem, thêm ga vào tuyến hoặc xóa các dòng đó (DELETE FROM dbo.trip_stations WHERE route_station_id IS NULL), rồi chạy lại script.', 1;

/* 3b. (Chỉ để xem khi bước 3 báo lỗi)
SELECT ts.trip_station_id, ts.trip_id, t.route_id, ts.station_id
FROM dbo.trip_stations ts JOIN dbo.train_trips t ON t.trip_id = ts.trip_id
WHERE ts.route_station_id IS NULL;
*/

/* 4. Bỏ khóa ngoại, unique constraint, index đang dùng station_id / station_order / route_station_id */
EXEC(N'
DECLARE @sql NVARCHAR(MAX) = N'''';

SELECT @sql += N''ALTER TABLE dbo.trip_stations DROP CONSTRAINT '' + QUOTENAME(fk.name) + N'';'' + CHAR(10)
FROM sys.foreign_keys fk
     JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
     JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
WHERE fk.parent_object_id = OBJECT_ID(''dbo.trip_stations'')
  AND c.name IN (''station_id'', ''route_station_id'');

SELECT @sql += N''ALTER TABLE dbo.trip_stations DROP CONSTRAINT '' + QUOTENAME(kc.name) + N'';'' + CHAR(10)
FROM sys.key_constraints kc
WHERE kc.parent_object_id = OBJECT_ID(''dbo.trip_stations'')
  AND kc.type = ''UQ''
  AND EXISTS (SELECT 1
              FROM sys.index_columns ic
                   JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
              WHERE ic.object_id = kc.parent_object_id
                AND ic.index_id = kc.unique_index_id
                AND c.name IN (''station_id'', ''station_order'', ''route_station_id''));

SELECT @sql += N''DROP INDEX '' + QUOTENAME(i.name) + N'' ON dbo.trip_stations;'' + CHAR(10)
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID(''dbo.trip_stations'')
  AND i.is_primary_key = 0
  AND i.is_unique_constraint = 0
  AND i.name IS NOT NULL
  AND EXISTS (SELECT 1
              FROM sys.index_columns ic
                   JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
              WHERE ic.object_id = i.object_id
                AND ic.index_id = i.index_id
                AND c.name IN (''station_id'', ''station_order'', ''route_station_id''));

EXEC sp_executesql @sql;
');

/* 5. Xóa 2 cột dư thừa */
IF COL_LENGTH('dbo.trip_stations', 'station_id') IS NOT NULL
    EXEC(N'ALTER TABLE dbo.trip_stations DROP COLUMN station_id');

IF COL_LENGTH('dbo.trip_stations', 'station_order') IS NOT NULL
    EXEC(N'ALTER TABLE dbo.trip_stations DROP COLUMN station_order');

/* 6. route_station_id bắt buộc + khóa ngoại + unique + index (tên khớp với entity TripStation) */
EXEC(N'ALTER TABLE dbo.trip_stations ALTER COLUMN route_station_id BIGINT NOT NULL');

EXEC(N'ALTER TABLE dbo.trip_stations
       ADD CONSTRAINT fk_trip_stations_route_station
           FOREIGN KEY (route_station_id) REFERENCES dbo.route_stations (route_station_id)');

EXEC(N'ALTER TABLE dbo.trip_stations
       ADD CONSTRAINT uk_trip_stations_trip_route_station UNIQUE (trip_id, route_station_id)');

EXEC(N'CREATE INDEX idx_trip_stations_route_station ON dbo.trip_stations (route_station_id)');

/* 7. Kiểm tra: phải còn đúng 5 cột trip_station_id, trip_id, route_station_id, arrival_time, departure_time */
SELECT c.name AS column_name, t.name AS data_type, c.is_nullable
FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.trip_stations')
ORDER BY c.column_id;
