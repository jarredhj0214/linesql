INSERT INTO audit.xml_docs (document_text, doc_id)
SELECT XMLSERIALIZE(CONTENT XMLELEMENT(NAME item,
           XMLATTRIBUTES(d.id AS id, d.category AS category),
           XMLFOREST(d.title AS title, d.summary AS summary), d.notes
       ) AS text INDENT), d.id
FROM raw.docs d;
