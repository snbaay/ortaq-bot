ALTER TABLE purchase ADD COLUMN receipt_file_id VARCHAR(512);
ALTER TABLE purchase ADD COLUMN receipt_mime VARCHAR(32);
ALTER TABLE purchase ADD COLUMN receipt_bytes BIGINT;
ALTER TABLE purchase ADD CONSTRAINT purchase_photo_metadata CHECK (
    (receipt_file_id IS NULL AND receipt_mime IS NULL AND receipt_bytes IS NULL)
    OR (receipt_file_id IS NOT NULL AND receipt_mime = 'image/jpeg' AND receipt_bytes BETWEEN 1 AND 10485760)
);
