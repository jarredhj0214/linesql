SELECT p.id
FROM ods.products AS p
WHERE p.name ILIKE p.pattern ESCAPE p.escape_char
  AND p.price BETWEEN SYMMETRIC p.lower_price AND p.upper_price
  AND p.removed_at ISNULL
  AND p.name NOTNULL;
