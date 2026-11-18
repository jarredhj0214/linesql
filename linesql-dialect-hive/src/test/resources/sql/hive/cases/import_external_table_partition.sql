IMPORT EXTERNAL TABLE `ods`.`orders_restore` PARTITION (dt='2026-11-18', region='cn')
FROM '/archive/orders' LOCATION '/warehouse/orders_restore';
