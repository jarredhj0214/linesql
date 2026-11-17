SELECT *
FROM jdbc.`live.data`.events a JOIN jdbc.live.`data.events` b ON a.id = b.id;
