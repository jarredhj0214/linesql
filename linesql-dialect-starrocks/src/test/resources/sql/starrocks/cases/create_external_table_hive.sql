create external table hive_users
(
  user_id bigint,
  user_name varchar(128),
  dt date
)
engine = hive
properties
(
  "resource" = "hive0",
  "database" = "ods",
  "table" = "users"
);
