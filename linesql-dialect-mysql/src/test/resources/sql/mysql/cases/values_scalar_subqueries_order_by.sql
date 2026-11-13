values row((select max(a.amount) from ods.a a)),
       row((select max(b.total) from ods.b b))
order by column_0;
