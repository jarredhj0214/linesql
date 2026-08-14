create view ads.active_view as
with active as (
  select id, name from ods.users where status = 'active'
)
select id, name from active
