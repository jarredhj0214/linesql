update low_priority ignore mart.users u
join staging.users_delta s on u.id = s.id
set u.name = s.name
where s.batch_id = 20260821;
