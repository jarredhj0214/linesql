create function mart.user_ids()
returns table (id int)
return select id from ods.users
