delete from ads.user_summary t
where exists (
  select 1
  from ods.deleted_users s
  where s.id = t.user_id
)
