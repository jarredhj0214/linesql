STATEMENT SET
BEGIN
INSERT INTO ads.user_summary (user_id, user_name)
SELECT id, name FROM ods.users;
INSERT OVERWRITE ads.order_summary (order_id, amount)
SELECT id, amount FROM ods.orders;
END
