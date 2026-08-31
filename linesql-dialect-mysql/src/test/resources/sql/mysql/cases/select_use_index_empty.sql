select id, name
from app.users use index ()
where status = 'ACTIVE';
