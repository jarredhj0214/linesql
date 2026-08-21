copy mart.users (id, name)
to '/tmp/users_export.csv'
with (format csv, header true);
