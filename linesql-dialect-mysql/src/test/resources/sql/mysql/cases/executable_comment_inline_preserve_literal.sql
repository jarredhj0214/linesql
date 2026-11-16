SELECT/*!80000 STRAIGHT_JOIN*/ CONCAT(e.payload, '/*!80000 leave this literal */') AS label
FROM ods.events e
WHERE e.active = 1;
