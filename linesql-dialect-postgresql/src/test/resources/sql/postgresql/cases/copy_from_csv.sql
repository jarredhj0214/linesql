copy mart.users (id, name, created_at)
from '/tmp/users.csv'
with (format csv, header true, delimiter ',');
