/* =====================================================================
   Train-Office — Sửa lỗi 500 ở /admin/payments
   ---------------------------------------------------------------------
   Code mới (commit 916ea8d) đổi Payment.paymentId từ Long sang UUID,
   nhưng ddl-auto=update KHÔNG đổi kiểu cột đã có, nên DB vẫn là
   payments.payment_id BIGINT -> Hibernate đọc ra lỗi -> 500.

   Script này thay cho sql/2026-10-28_payment_id_to_uuid.sql:
     - Xóa đủ PK / FK / unique / index đang bám vào cột payment_id
       (script cũ bị lỗi ở bước DROP COLUMN invoices.payment_id vì
        còn index idx_invoices_payment và unique constraint).
     - Chạy trong 1 transaction: lỗi giữa chừng thì rollback hết.
     - Dọn được cả trạng thái dở dang nếu script cũ đã chạy hỏng.

   Cách chạy (SQL Server):
     1. TẮT ứng dụng.
     2. Mở file trong SSMS / DataGrip / IntelliJ Database, Run (F5).
     3. Khởi động lại ứng dụng.
   ===================================================================== */

USE train_office;
GO
SET XACT_ABORT ON;
GO

-- 0. Tình trạng hiện tại (kiểu của cột payment_id ở 2 bảng)
SELECT t.name AS table_name, c.name AS column_name, ty.name AS type_name
FROM sys.columns c
JOIN sys.tables t  ON t.object_id = c.object_id
JOIN sys.types  ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN ('payments', 'invoices')
  AND c.name IN ('payment_id', 'payment_id_uuid');
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns c
    JOIN sys.types t ON t.user_type_id = c.user_type_id
    WHERE c.object_id = OBJECT_ID('dbo.payments')
      AND c.name = 'payment_id'
      AND t.name <> 'uniqueidentifier'
)
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @sql NVARCHAR(MAX);

        -- 1. Dọn cột tạm còn sót lại nếu script cũ đã chạy hỏng
        SET @sql = N'';
        SELECT @sql += N'ALTER TABLE dbo.' + QUOTENAME(t.name)
                     + N' DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
        FROM sys.default_constraints dc
        JOIN sys.tables  t ON t.object_id = dc.parent_object_id
        JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
        WHERE t.name IN ('payments', 'invoices') AND c.name = 'payment_id_uuid';
        EXEC sp_executesql @sql;

        IF COL_LENGTH('dbo.payments', 'payment_id_uuid') IS NOT NULL
            EXEC(N'ALTER TABLE dbo.payments DROP COLUMN payment_id_uuid;');
        IF COL_LENGTH('dbo.invoices', 'payment_id_uuid') IS NOT NULL
            EXEC(N'ALTER TABLE dbo.invoices DROP COLUMN payment_id_uuid;');

        -- 2. Xóa mọi khóa ngoại trỏ tới bảng payments
        SET @sql = N'';
        SELECT @sql += N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id))
                     + N'.' + QUOTENAME(OBJECT_NAME(fk.parent_object_id))
                     + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
        FROM sys.foreign_keys fk
        WHERE fk.referenced_object_id = OBJECT_ID('dbo.payments');
        EXEC sp_executesql @sql;

        -- 3. Xóa PK / unique constraint / index có chứa cột payment_id
        SET @sql = N'';
        SELECT @sql += CASE
                          WHEN i.is_primary_key = 1 OR i.is_unique_constraint = 1
                              THEN N'ALTER TABLE dbo.' + QUOTENAME(t.name)
                                 + N' DROP CONSTRAINT ' + QUOTENAME(i.name) + N';'
                          ELSE N'DROP INDEX ' + QUOTENAME(i.name)
                             + N' ON dbo.' + QUOTENAME(t.name) + N';'
                      END
        FROM sys.indexes i
        JOIN sys.tables t ON t.object_id = i.object_id
        WHERE t.name IN ('payments', 'invoices')
          AND i.name IS NOT NULL
          AND EXISTS (
                SELECT 1
                FROM sys.index_columns ic
                JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id
                  AND ic.index_id  = i.index_id
                  AND c.name = 'payment_id');
        EXEC sp_executesql @sql;

        -- 4. Thêm cột UUID, sinh giá trị cho từng payment
        EXEC(N'ALTER TABLE dbo.payments ADD payment_id_uuid UNIQUEIDENTIFIER NULL;');
        EXEC(N'UPDATE dbo.payments SET payment_id_uuid = NEWID();');

        -- 5. invoices: ánh xạ payment_id cũ (bigint) sang UUID mới
        IF COL_LENGTH('dbo.invoices', 'payment_id') IS NOT NULL
        BEGIN
            EXEC(N'ALTER TABLE dbo.invoices ADD payment_id_uuid UNIQUEIDENTIFIER NULL;');
            EXEC(N'UPDATE i SET i.payment_id_uuid = p.payment_id_uuid
                   FROM dbo.invoices i
                   JOIN dbo.payments p ON i.payment_id = p.payment_id;');
            EXEC(N'ALTER TABLE dbo.invoices DROP COLUMN payment_id;');
            EXEC sp_rename 'dbo.invoices.payment_id_uuid', 'payment_id', 'COLUMN';
        END

        -- 6. payments: thay cột cũ bằng cột UUID, tạo lại PK
        EXEC(N'ALTER TABLE dbo.payments DROP COLUMN payment_id;');
        EXEC sp_rename 'dbo.payments.payment_id_uuid', 'payment_id', 'COLUMN';
        EXEC(N'ALTER TABLE dbo.payments ALTER COLUMN payment_id UNIQUEIDENTIFIER NOT NULL;');
        EXEC(N'ALTER TABLE dbo.payments ADD CONSTRAINT PK_payments PRIMARY KEY (payment_id);');

        -- 7. invoices: NOT NULL + unique + index + FK như entity Invoice
        IF COL_LENGTH('dbo.invoices', 'payment_id') IS NOT NULL
        BEGIN
            EXEC(N'ALTER TABLE dbo.invoices ALTER COLUMN payment_id UNIQUEIDENTIFIER NOT NULL;');
            EXEC(N'ALTER TABLE dbo.invoices ADD CONSTRAINT UK_invoices_payment UNIQUE (payment_id);');
            EXEC(N'CREATE INDEX idx_invoices_payment ON dbo.invoices (payment_id);');
            EXEC(N'ALTER TABLE dbo.invoices ADD CONSTRAINT FK_invoices_payments
                   FOREIGN KEY (payment_id) REFERENCES dbo.payments (payment_id);');
        END

        COMMIT TRANSACTION;
        PRINT N'Đã chuyển payment_id sang UNIQUEIDENTIFIER thành công.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        PRINT N'LỖI - đã rollback, DB giữ nguyên như trước khi chạy:';
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT N'payments.payment_id đã là UNIQUEIDENTIFIER (hoặc chưa có bảng payments) - không cần làm gì.';
END
GO

-- 8. Kiểm tra lại
SELECT t.name AS table_name, c.name AS column_name, ty.name AS type_name, c.is_nullable
FROM sys.columns c
JOIN sys.tables t  ON t.object_id = c.object_id
JOIN sys.types  ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN ('payments', 'invoices')
  AND c.name = 'payment_id';
GO
