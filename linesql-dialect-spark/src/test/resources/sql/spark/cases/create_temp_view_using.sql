create or replace temporary view tmp_csv_users
using csv
options (
  path '/tmp/users.csv',
  header 'true'
)
