with user_data (uid, uname) as (
  select id, name from ods.users
)
select uid, uname from user_data
