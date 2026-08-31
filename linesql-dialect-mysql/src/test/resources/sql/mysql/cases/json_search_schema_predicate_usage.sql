select id
from app.events
where json_search(payload, 'one', search_text, null, '$.tags[*]') is not null
  and json_schema_valid(schema_doc, payload)
  and json_length(payload, '$.items') > 0
