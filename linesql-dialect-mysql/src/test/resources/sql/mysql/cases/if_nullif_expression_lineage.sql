select if(is_vip = 1, vip_score, base_score) as score,
       nullif(primary_email, backup_email) as normalized_email
from app.users
where deleted_at is null
