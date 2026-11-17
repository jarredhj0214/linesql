SELECT a.booking_id,
       (a.starts_at, a.ends_at) OVERLAPS (b.starts_at, b.ends_at) AS conflicts
FROM mart.bookings AS a
JOIN staging.proposed_bookings AS b ON (a.hotel_id, a.room_id) = (b.hotel_id, b.room_id)
WHERE (a.room_id, a.status) IS DISTINCT FROM (b.room_id, b.status)
ORDER BY a.booking_id;
