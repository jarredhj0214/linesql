select id
from app.users
where name like 'A\\_%' escape '\\';
