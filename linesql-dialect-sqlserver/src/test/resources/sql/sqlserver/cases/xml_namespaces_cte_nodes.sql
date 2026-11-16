with xmlnamespaces ('urn:orders' as o, default 'urn:items'),
docs as (select order_xml from ods.orders)
select n.node.value('(@sku)[1]', 'varchar(40)') as sku
from docs d
cross apply d.order_xml.nodes('/o:order/item') n(node);
