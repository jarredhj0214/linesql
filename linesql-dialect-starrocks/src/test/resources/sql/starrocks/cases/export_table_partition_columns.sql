export table mart.orders partition (p202609) (order_id, amount)
to 's3://bucket/export/orders/'
properties (
  "column_separator" = ",",
  "line_delimiter" = "\n"
);
