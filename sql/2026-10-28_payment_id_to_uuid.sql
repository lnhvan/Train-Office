/* =====================================================================
   Train-Office — Chuyển payment_id sang kiểu UNIQUEIDENTIFIER (UUID)
   ---------------------------------------------------------------------
   Trước: payments.payment_id (BIGINT IDENTITY), invoices.payment_id (BIGINT)
   Sau:   payments.payment_id (UNIQUEIDENTIFIER DEFAULT NEWID()),
          invoices.payment_id (UNIQUEIDENTIFIER)
          Đồng bộ payment_id làm vnp_TxnRef cho VNPay.

   Cách chạy (SQL Server, database train_office):
     1. TẮT ứng dụng.
     2. Mở file này trong SSMS / DataGrip / IntelliJ Database Tool và bấm Run / Execute (F5).
     3. Khởi động lại ứng dụng.
   ===================================================================== */

USE train_office;

-- 1. Kiểm tra xem payment_id đã là uniqueidentifier chưa
IF EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.types t ON c.user_type_id = t.user_type_id
    WHERE c.object_id = OBJECT_ID('dbo.payments')
      AND c.name = 'payment_id'
      AND t.name <> 'uniqueidentifier'
)
BEGIN
    PRINT N'Bắt đầu chuyển đổi payments.payment_id sang UNIQUEIDENTIFIER...';

    -- Xóa khóa ngoại từ bảng invoices nếu có
    DECLARE @fk_name NVARCHAR(256);
    SELECT @fk_name = fk.name
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
    WHERE fkc.parent_object_id = OBJECT_ID('dbo.invoices')
      AND fkc.referenced_object_id = OBJECT_ID('dbo.payments');

    IF @fk_name IS NOT NULL
    BEGIN
        EXEC('ALTER TABLE dbo.invoices DROP CONSTRAINT ' + @fk_name);
        PRINT N'Đã xóa khóa ngoại: ' + @fk_name;
    END

    -- Xóa Primary Key trên bảng payments
    DECLARE @pk_name NVARCHAR(256);
    SELECT @pk_name = kc.name
    FROM sys.key_constraints kc
    WHERE kc.parent_object_id = OBJECT_ID('dbo.payments') AND kc.type = 'PK';

    IF @pk_name IS NOT NULL
    BEGIN
        EXEC('ALTER TABLE dbo.payments DROP CONSTRAINT ' + @pk_name);
        PRINT N'Đã xóa Primary Key: ' + @pk_name;
    END

    -- Thêm cột UUID tạm thời
    ALTER TABLE dbo.payments ADD payment_id_uuid UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID();

    -- Đồng bộ transaction_code nếu chưa có
    EXEC('UPDATE dbo.payments SET transaction_code = CAST(payment_id_uuid AS NVARCHAR(100)) WHERE transaction_code IS NULL OR transaction_code = ''''');

    -- Nếu bảng invoices đang tham chiếu payment_id, cập nhật tương ứng
    IF COL_LENGTH('dbo.invoices', 'payment_id') IS NOT NULL
    BEGIN
        ALTER TABLE dbo.invoices ADD payment_id_uuid UNIQUEIDENTIFIER NULL;
        EXEC('UPDATE i SET i.payment_id_uuid = p.payment_id_uuid FROM dbo.invoices i JOIN dbo.payments p ON i.payment_id = p.payment_id');
        ALTER TABLE dbo.invoices DROP COLUMN payment_id;
        EXEC sp_rename 'dbo.invoices.payment_id_uuid', 'payment_id', 'COLUMN';
    END

    -- Xóa cột payment_id cũ và đổi tên cột UUID thành payment_id
    ALTER TABLE dbo.payments DROP COLUMN payment_id;
    EXEC sp_rename 'dbo.payments.payment_id_uuid', 'payment_id', 'COLUMN';

    -- Tạo lại Primary Key
    ALTER TABLE dbo.payments ADD CONSTRAINT PK_payments PRIMARY KEY (payment_id);

    -- Tạo lại Khóa ngoại từ invoices -> payments
    IF COL_LENGTH('dbo.invoices', 'payment_id') IS NOT NULL
        ALTER TABLE dbo.invoices ADD CONSTRAINT FK_invoices_payments FOREIGN KEY (payment_id) REFERENCES dbo.payments(payment_id);

    PRINT N'Chuyển đổi hoàn tất thành công!';
END
ELSE
BEGIN
    PRINT N'Cột payment_id đã là UNIQUEIDENTIFIER hoặc bảng payments chưa được tạo.';
END
GO
