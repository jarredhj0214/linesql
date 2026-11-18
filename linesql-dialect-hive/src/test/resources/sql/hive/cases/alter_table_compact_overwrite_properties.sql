ALTER TABLE mart.orders PARTITION (dt = '2026-11-18')
COMPACT 'major' WITH OVERWRITE TBLPROPERTIES ('tblprops.orc.compress.size' = '8192');
