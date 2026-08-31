select
  user_id,
  array_join(tags, ',') as tag_csv,
  arrays_zip(scores, weights) as weighted_pairs
from dwd.user_features
where all_match(scores, s -> s > min_score)
