copy mart.users (id, name)
to program 'gzip > /tmp/users.csv.gz'
with (format csv, delimiter ',');
