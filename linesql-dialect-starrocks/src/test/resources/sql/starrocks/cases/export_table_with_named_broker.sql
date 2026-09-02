export table mart.orders (order_id, amount, updated_at)
to "hdfs://warehouse/export/orders/"
properties
(
  "column_separator" = "\x01",
  "line_delimiter" = "\n"
)
with broker "hdfs_broker"
(
  "username" = "hdfs",
  "password" = "secret"
);
