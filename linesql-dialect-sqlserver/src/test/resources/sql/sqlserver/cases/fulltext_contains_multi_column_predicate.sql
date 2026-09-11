select p.product_id
from dbo.products p
where contains((p.title, p.description), '"governance*"');
