create dictionary dim_user_dict using dim.users (
  user_id key,
  user_name value,
  user_level value
)
properties (
  "dictionary_warm_up" = "true",
  "dictionary_refresh_interval" = "3600"
);
