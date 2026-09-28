insert into app.users (id, display_name)
values (42, 'Ada')
returning id, display_name into v_id, v_name;
