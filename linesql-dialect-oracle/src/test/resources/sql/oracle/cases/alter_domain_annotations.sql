ALTER USECASE DOMAIN IF EXISTS workflow.ticket_status
ANNOTATIONS (ADD OR REPLACE title 'Ticket state', DROP IF EXISTS retired);
