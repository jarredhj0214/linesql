update mart.users u
join app.users_delta d on u.id = d.id
set u.name = d.name,
    d.synced_at = u.updated_at
where d.status = 'READY';
