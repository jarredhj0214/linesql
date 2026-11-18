SHOW COMPACTIONS `sales`.`orders` PARTITION (ds = '2026-09-10')
POOL 'nightly' TYPE 'minor' STATUS 'ready for cleaning'
ORDER BY cq_table DESC, cq_state LIMIT 20
