delete from mart.users
using mart.users
join app.deleted_users d on mart.users.id = d.id
