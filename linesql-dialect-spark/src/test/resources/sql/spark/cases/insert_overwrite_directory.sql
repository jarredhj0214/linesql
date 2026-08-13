insert overwrite directory '/tmp/linesql/users'
using parquet
select id, name
from ods.users
