select transform (id, name)
using 'cat'
as (id string, name string)
from ods.users
