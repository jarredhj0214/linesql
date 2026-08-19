with deleted as (
  select id
  from staging.deleted_users
  where reason = 'GDPR'
)
delete from mart.users t
using deleted d
where t.id = d.id
returning t.id;
