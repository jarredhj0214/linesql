DROP PROCEDURE IF EXISTS mart.refresh_orders;
DELIMITER '$$'
CREATE PROCEDURE mart.refresh_orders()
BEGIN
    INSERT INTO mart.audit (id) SELECT id FROM ods.orders;
    SELECT 'inside
DELIMITER //
$$' AS message;
END$$
\d "//"
SELECT amount--discount AS net FROM ods.sales//
DELIMITER ;
SELECT id FROM ods.refunds;
