select *
from (
  select t.tag_code,
         group_concat(t.tag_value_code separator '、') as tag_value_code
  from app.tag_values t
  group by t.tag_code
) q
order by q.tag_code
limit 0, 10;
