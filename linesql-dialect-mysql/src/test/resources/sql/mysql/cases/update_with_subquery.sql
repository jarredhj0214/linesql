update ads.user_summary
set user_name = (select name from ods.users where ods.users.id = ads.user_summary.user_id)
where exists (select 1 from ods.users where ods.users.id = ads.user_summary.user_id)
