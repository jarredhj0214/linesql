create database link remote_dw
connect to dw_user identified by "secret"
using 'remote_service';
