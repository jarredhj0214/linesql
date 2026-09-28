select e.id
from mart.events e
where row(e.tenant_id, e.event_key) = row(e.expected_tenant_id, e.expected_event_key)
