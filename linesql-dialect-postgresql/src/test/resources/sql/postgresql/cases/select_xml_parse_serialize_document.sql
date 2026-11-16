SELECT XMLSERIALIZE(DOCUMENT XMLPARSE(DOCUMENT d.body) AS text NO INDENT) AS pretty,
       d.payload IS DOCUMENT AS is_full_document,
       XML '<marker/>' AS marker
FROM raw.docs d
WHERE d.payload IS NOT DOCUMENT;
