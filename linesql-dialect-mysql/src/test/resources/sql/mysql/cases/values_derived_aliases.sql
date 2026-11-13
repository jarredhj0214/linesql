select v.id, v.amount
from (values row(1, (select max(a.amount) from ods.a a)),
             row(2, (select max(b.total) from ods.b b))) as v(id, amount);
