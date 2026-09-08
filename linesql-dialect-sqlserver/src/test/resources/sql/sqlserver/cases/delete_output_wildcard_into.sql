delete from ads.user_summary
output deleted.*
into audit.deleted_users
where dt = '20260908';
