values ((select max(a.amount) from ods.orders a)),
       ((select max(b.amount) from ods.archive b))
order by column1;
