select u.id, tag.value
from public.users u
cross join lateral jsonb_array_elements_text(u.tags) as tag(value);
