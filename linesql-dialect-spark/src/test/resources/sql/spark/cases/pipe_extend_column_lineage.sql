from ods.users
|> extend upper(name) as name_upper
|> select id, name_upper
