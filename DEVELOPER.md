

# PostgreSQL Setup

Default commands to set up pgsql for gokb follow:

    sudo -u postgres psql postgres


    psql (9.5.4)
    Type "help" for help.

    postgres=# CREATE DATABASE gokb;
    CREATE DATABASE
    postgres=# CREATE USER knowint WITH PASSWORD 'knowint';
    CREATE ROLE
    postgres=# GRANT ALL PRIVILEGES ON DATABASE gokb to knowint;
    GRANT
    \q


Connect with

psql -h localhost -U knowint gokb

