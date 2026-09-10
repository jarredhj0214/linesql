select user_id, event_time
from ods.events tablesample bernoulli (5);
