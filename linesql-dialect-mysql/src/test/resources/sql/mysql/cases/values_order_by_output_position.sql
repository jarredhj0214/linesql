values row((select max(a.amount) from ods.a a), (select max(b.amount) from ods.b b)),
       row(0, (select max(c.amount) from ods.c c))
order by 2 desc;
