select p.n.query('.') as node_xml
from (select d.document from ods.documents d) as src(payload)
cross apply src.payload.nodes('/root/item') as p(n);
