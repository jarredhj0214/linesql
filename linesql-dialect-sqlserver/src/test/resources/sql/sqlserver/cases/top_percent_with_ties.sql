select top (10) percent with ties id, score
from dbo.user_scores
order by score desc;
