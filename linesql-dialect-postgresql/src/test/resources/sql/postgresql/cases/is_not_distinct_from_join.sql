select l.id as left_id, r.value as right_value
from stage.left_rows l
join stage.right_rows r
  on l.match_key is not distinct from r.match_key
