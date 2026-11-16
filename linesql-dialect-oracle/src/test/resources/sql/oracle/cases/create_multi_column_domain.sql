CREATE DOMAIN billing.invoice_key AS (
    region AS VARCHAR2(16) STRICT NOT NULL,
    invoice_no AS NUMBER(18) CHECK (invoice_no > 0)
)
DISPLAY region || ':' || invoice_no
ORDER invoice_no;
