select transform (id, payload)
using 'python mapper.py'
as key, value
from ods.events
where dt = '${bizdate}';
