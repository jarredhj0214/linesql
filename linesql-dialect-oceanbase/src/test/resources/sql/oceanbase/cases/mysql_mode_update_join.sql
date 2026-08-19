update mart.users t
join staging.users_delta s on t.id = s.id
set t.name = s.name
