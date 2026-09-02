create dictionary dim_user_mv_dict using dim.mv_users (
  user_id key,
  user_level value,
  latest_order_time value
)
properties (
  "dictionary_warm_up" = "false",
  "dictionary_refresh_interval" = "0"
);
