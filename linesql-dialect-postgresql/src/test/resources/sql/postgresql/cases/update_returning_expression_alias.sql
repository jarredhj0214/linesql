update mart.users t
set name = trim(t.name)
where t.id > 0
returning t.id as user_id, upper(t.name) as normalized_name
