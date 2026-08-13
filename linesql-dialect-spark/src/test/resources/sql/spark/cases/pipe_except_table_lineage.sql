from ods.users
|> select id as user_id
|> except select id as user_id from ods.deleted_users
