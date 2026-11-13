select x.ord, x.amount
from ods.order_payloads p,
     xmltable(xmlnamespaces('urn:orders' as ns),
       '$doc/ns:orders/ns:order[ns:id = $wanted]'
       passing by value p.payload as "doc", p.order_id as "wanted"
       returning sequence by ref
       columns ord for ordinality,
               amount number path 'ns:amount'
     ) x
where x.amount > 0;
