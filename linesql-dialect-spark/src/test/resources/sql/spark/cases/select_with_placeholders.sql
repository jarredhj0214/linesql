select id, name
from ods.users
where dt = ${bizdate}
  and region = {{ region }}
