select
  timestampadd(day, retry_count, created_at) as retry_deadline,
  timestampdiff(hour, created_at, updated_at) as age_hours
from app.orders;
