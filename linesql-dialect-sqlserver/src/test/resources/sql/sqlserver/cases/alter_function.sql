alter function dbo.f_discount(@amount decimal(18,2))
returns decimal(18,2)
as
begin
    return @amount
end;
