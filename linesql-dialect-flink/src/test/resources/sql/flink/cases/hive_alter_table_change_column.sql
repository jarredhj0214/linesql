ALTER TABLE ods.raw_orders CHANGE COLUMN amount pay_amount DECIMAL(18, 2) COMMENT 'payment amount' AFTER order_id CASCADE
