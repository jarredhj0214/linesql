select x.n.query('.') as node_xml
from @xml.nodes('/root/item') as x(n);
