delete from mart.users, mart.user_acl
using mart.users
join mart.user_acl on mart.user_acl.user_id = mart.users.id
join app.deleted_users d on d.id = mart.users.id
where d.deleted_at < now() - interval 30 day;
