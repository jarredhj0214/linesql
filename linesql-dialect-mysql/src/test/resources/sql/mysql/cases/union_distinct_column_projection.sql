select email as contact
from app.users
union distinct
select buyer_email
from app.orders;
