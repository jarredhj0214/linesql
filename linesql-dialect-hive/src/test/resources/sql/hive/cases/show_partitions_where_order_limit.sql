SHOW PARTITIONS `mart`.`orders` PARTITION (ds = '2026-09-10')
WHERE hr >= 10 AND region = 'eu'
ORDER BY hr DESC, ds
LIMIT 10
