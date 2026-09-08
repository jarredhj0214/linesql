SELECT id, parent_id, name
FROM app.org_units
START WITH parent_id IS NULL
CONNECT BY PRIOR id = parent_id
ORDER SIBLINGS BY name;
