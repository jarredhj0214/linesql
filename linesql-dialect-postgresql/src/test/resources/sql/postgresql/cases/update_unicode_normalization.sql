UPDATE mart.contacts t
SET display_name = NORMALIZE(s.raw_name, NFKC)
FROM ods.contacts s
WHERE t.id = s.id AND s.raw_name IS NOT NFKC NORMALIZED;
