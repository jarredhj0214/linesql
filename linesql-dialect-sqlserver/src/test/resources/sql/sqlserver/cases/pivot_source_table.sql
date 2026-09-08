select *
from (
    select region, amount
    from ods.sales
) s
pivot (
    sum(amount) for region in ([North], [South])
) p;
