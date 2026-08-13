from ods.users
|> select id as user_id
|> union table ods.admins
