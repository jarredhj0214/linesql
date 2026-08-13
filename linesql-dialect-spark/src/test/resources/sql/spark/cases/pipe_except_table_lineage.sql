from ods.users
|> select id as user_id
|> except table ods.deleted_users
