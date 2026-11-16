select id, locations.value('(@id)[1]', 'int') as location_id,
       st.step.query('.') as step_xml
from ods.orders
cross apply payload.nodes('/order/locations/location') as l(locations)
cross apply l.locations.nodes('./step') as st(step)
where st.step.exist('@done') = 1;
