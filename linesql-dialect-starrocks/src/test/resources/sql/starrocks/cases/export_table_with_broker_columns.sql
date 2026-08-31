export table mart.orders partition(p202401, p202402) (order_id, amount)
to "hdfs://warehouse/export/orders/"
properties ("column_separator" = ",")
with broker
(
  "username" = "hdfs",
  "password" = "secret"
)
