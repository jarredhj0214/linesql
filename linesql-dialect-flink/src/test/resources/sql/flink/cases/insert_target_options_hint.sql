INSERT INTO kafka_sink /*+ OPTIONS('sink.partitioner'='round-robin') */
SELECT id, name
FROM kafka_source
