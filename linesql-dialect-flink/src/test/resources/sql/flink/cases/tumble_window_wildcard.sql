select w.*
from tumble(table ods.orders, descriptor(ts), interval '1' hour) w;
