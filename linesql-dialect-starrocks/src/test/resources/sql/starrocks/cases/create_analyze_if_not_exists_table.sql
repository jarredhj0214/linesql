create analyze sample if not exists table ads.orders(order_id, user_id)
properties
(
  "statistic_sample_collect_rows" = "1000000"
);
