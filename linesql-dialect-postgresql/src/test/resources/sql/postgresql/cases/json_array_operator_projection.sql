SELECT e.payload ->> 'name' AS customer_name,
       e.tags[1] AS first_tag,
       e.metrics[1:2] AS metric_slice
FROM app.events e
WHERE e.payload ? 'name'
  AND e.attrs @> '{"channel":"web"}';
