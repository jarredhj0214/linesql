from ods.users
|> select id as user_id
|> intersect select id as user_id from ods.active_users
