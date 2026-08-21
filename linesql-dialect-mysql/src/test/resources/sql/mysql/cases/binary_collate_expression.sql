select binary name as raw_name
from app.users
where name collate utf8mb4_bin = 'Alice';
