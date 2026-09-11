select p.product_id, p.title
from dbo.products p
where contains(p.description, '"bike*"')
  and freetext(p.notes, 'mountain trail');
