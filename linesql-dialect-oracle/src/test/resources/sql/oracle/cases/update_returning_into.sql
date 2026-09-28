update app.users u
set display_name = upper(u.pending_name)
where u.id = 42
returning u.id, u.display_name into v_id, v_name;
