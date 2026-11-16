UPDATE mart.docs d
SET has_items = XMLEXISTS(s.row_xpath PASSING BY REF s.payload BY VALUE)
FROM staging.documents s
WHERE d.id = s.doc_id AND XMLEXISTS('/doc' PASSING s.payload BY REF);
