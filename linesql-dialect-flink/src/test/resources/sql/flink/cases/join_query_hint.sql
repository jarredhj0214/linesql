SELECT /*+ BROADCAST(t2) */ t1.id, t2.name
FROM kafka_table1 t1
JOIN kafka_table2 t2
ON t1.id = t2.id
