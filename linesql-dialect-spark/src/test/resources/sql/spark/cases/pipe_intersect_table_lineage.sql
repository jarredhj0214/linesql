from ods.users
|> select id as user_id
|> intersect table ods.active_users
