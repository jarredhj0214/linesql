select e.id, jt.name
from ods.events e,
json_table(
  e.payload,
  '$.items[*]'
  columns (name string path '$.name')
) jt
