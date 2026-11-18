FROM ods.users
|> SELECT name AS display_name
|> EXTEND upper(display_name) AS name_upper;
