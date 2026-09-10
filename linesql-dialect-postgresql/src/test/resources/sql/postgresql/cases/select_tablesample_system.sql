select id, amount
from mart.orders tablesample system (10) repeatable (42)
where amount > 0;
