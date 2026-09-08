select id, amount
from ods.orders
order by amount desc
fetch first 10 percent rows only;
