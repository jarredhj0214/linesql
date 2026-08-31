select str_to_date(date_text, get_format(date, 'USA')) as parsed_date
from app.raw_events;
