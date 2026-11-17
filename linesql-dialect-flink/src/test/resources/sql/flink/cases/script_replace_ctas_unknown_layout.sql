CREATE TABLE mart.snapshot (old_id INT) WITH ('connector'='kafka');
REPLACE TABLE mart.snapshot WITH ('connector'='kafka') AS SELECT * FROM ods.orders;
SELECT * FROM mart.snapshot;
