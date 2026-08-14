delete from ads.user_summary
where user_id in (select id from ods.deleted_users)
