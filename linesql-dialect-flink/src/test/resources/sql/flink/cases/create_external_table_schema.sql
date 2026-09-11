CREATE EXTERNAL TABLE ext.kafka_orders (
    order_id STRING,
    payload STRING
) WITH (
    'connector' = 'kafka',
    'topic' = 'orders'
);
