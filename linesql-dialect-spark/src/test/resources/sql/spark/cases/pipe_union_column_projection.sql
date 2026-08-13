from ods.users
|> select id as user_id
|> union all select id as user_id from ods.admins
