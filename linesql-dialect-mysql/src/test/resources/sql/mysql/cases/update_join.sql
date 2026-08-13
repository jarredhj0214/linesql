update mart.users u
join app.users_delta d on u.id = d.id
set u.name = d.name
