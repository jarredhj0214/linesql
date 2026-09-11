CREATE TEMPORARY TABLE tmp.session_orders (
    order_id STRING,
    user_id STRING,
    order_time TIMESTAMP(3),
    WATERMARK FOR order_time AS order_time - INTERVAL '5' SECOND
) WITH (
    'connector' = 'datagen'
);
