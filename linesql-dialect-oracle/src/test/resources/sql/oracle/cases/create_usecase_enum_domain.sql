CREATE USECASE DOMAIN IF NOT EXISTS workflow.ticket_status AS ENUM (
    queued = pending = 10,
    closed = 20
)
DEFAULT pending
ANNOTATIONS (ADD IF NOT EXISTS title 'Ticket status');
