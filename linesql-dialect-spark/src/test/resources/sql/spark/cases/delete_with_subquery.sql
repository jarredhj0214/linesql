delete from ads.users
where id in (
  select id
  from ods.deleted_users
)
