select u.id, o.id from ods.users u left join ods.orders o on u.id = o.user_id
