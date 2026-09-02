SELECT id, name
FROM kafka_table1 /*+ OPTIONS('scan.startup.mode'='earliest-offset') */
