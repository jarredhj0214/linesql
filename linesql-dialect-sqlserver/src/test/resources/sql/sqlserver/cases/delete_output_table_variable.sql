delete from ads.user_summary
output deleted.user_id
into @deleted_users (user_id)
where dt = '20260908';
