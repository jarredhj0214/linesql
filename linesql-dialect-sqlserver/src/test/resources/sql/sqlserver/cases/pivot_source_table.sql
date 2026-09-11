select p.[North] as north_amount, p.[South] as south_amount
from (
    select region, amount
    from ods.sales
) s
pivot (
    sum(amount) for region in ([North], [South])
) p;
