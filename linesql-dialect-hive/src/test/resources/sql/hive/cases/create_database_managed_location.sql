CREATE DATABASE IF NOT EXISTS `sales` COMMENT 'reporting' LOCATION '/warehouse/external/sales'
MANAGEDLOCATION '/warehouse/managed/sales' WITH DBPROPERTIES ('team' = 'reporting')
