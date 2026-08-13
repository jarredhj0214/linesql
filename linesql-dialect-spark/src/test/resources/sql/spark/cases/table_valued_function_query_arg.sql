select *
from custom_tvf(table(select id from ods.users))
