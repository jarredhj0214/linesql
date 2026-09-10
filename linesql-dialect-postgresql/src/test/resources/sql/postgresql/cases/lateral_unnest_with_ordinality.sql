select u.id, tag.item, tag.ord
from public.users u
cross join lateral unnest(u.tags) with ordinality as tag(item, ord);
