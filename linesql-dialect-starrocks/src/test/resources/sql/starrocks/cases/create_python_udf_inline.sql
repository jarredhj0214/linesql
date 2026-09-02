create function python_echo(int)
returns int
type = 'Python'
symbol = 'echo'
file = 'inline'
as
$$
def echo(x):
    return x
$$;
