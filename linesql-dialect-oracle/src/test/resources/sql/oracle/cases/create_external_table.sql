create table ext.order_payloads (
  id number,
  payload varchar2(4000)
)
organization external (
  type oracle_loader
  default directory data_dir
  access parameters (
    records delimited by newline
  )
  location ('orders.csv')
);
