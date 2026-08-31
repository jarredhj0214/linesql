alter materialized table ads.mt_order_amounts resume with (
  'sink.parallelism' = '4'
);
