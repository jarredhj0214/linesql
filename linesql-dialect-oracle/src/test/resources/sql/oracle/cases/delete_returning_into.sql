delete from app.users u
where u.status = 'INACTIVE'
returning u.id, u.display_name into v_id, v_name;
