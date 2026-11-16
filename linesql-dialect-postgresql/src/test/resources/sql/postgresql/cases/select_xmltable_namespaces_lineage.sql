SELECT x.order_number, x.position
FROM raw.docs d
CROSS JOIN LATERAL XMLTABLE(
    XMLNAMESPACES(d.namespace_uri AS n),
    d.row_xpath PASSING BY REF d.payload BY VALUE
    COLUMNS order_id integer PATH d.id_xpath DEFAULT d.default_id NOT NULL,
            seq FOR ORDINALITY
) AS x(order_number, position)
WHERE x.order_number > 0
ORDER BY x.position;
