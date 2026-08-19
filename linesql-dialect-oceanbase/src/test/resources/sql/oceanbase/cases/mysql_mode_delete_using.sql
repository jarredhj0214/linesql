delete from mart.users
using mart.users join staging.deleted_users s on users.id = s.user_id
