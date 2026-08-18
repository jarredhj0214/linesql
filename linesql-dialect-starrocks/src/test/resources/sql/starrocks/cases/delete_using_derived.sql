delete from ads.user_summary
using (
  select id
  from ods.users_delta
) u
where user_summary.user_id = u.id;
