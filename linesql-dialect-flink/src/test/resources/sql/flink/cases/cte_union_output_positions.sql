with history(batch_id, amount) as (
    select 0, a.amount from ods.a a
    union all
    select 1, b.total from ods.b b
)
select h.batch_id, h.amount from history h;
