delete from ads.user_summary
using ods.users u
where user_summary.user_id = u.id;
