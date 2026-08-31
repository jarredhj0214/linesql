select id, name
from app.users partition (p202608, pmax)
where status = 'ACTIVE';
