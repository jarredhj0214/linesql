select
  user_id
from dwd.user_tags
where any_match(tags, tag -> tag = target_tag);
