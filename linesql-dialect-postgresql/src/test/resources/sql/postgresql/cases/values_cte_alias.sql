with limits(max_amount) as (
    values ((select max(a.amount) from ods.orders a)),
           ((select max(b.amount) from ods.archive b))
)
select l.max_amount from limits l;
