ALTER TABLE IF EXISTS `lake`.`ods`.`orders` MODIFY DISTRIBUTION BY HASH(order_id, tenant_id);
