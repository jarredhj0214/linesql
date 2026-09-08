create or replace package app.pkg_orders as
    procedure refresh_orders(p_dt varchar2);
end pkg_orders;
