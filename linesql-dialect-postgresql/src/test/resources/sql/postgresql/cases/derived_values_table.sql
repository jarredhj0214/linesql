select v.id, v.name
from (values (1, 'alice'), (2, 'bob')) as v(id, name);
