create global temporary table tmp.session_orders (
  id number,
  amount number
) on commit preserve rows
