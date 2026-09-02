create dictionary dim_user_view_dict using dim.v_users (
  user_id key,
  user_name value,
  city value
)
properties (
  "dictionary_memory_limit" = "2000000000"
);
