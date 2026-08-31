create analyze sample if not exists table ads.users(user_id, region)
properties (
  "statistic_auto_collect_ratio" = "0.5"
);
