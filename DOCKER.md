
This document details one approach to running GOKb in a docker environment.

It is focused on the PostgreSQL oriented deployment, and is docker-centric in it's approach.


# PostgreSQL Instance

The standard docker postgres instance is sufficient for a starter installation. The following command will download the postgres image and then start the image. The container will auto start with docker (So always be available), will be named pghost, sets the default postgres password and exposes the postgres http port. The image runs as a daemon (-d). Note also, that the GOKb default datasource config looks for a postgres host called pghost. The instance is so named so these things align. If you choose a different name for your postgres host, or have postgres running on another host and don't wish to use this mechanism, you will need to us --add-host to add the pghost hostname to your gokb container.

    docker run --restart=always --name pghost -e POSTGRES_PASSWORD=ChangeMe -p 5432:5432 -d postgres

Having run this command, you will need to set up a database for gokb. Run

    psql -h localhost -U postgres

Use the password from the docker command (eg ChangeMe) and then run the following commands to set up your gokb database and users

    CREATE DATABASE gokb;
    CREATE USER knowint WITH PASSWORD 'knowint';
    GRANT ALL PRIVILEGES ON DATABASE gokb to knowint;
    \q

This creates the appropriate user accounts

## Testing Domain Configuration

You can start a vanilla ubuntu with the following command

    docker run -t --link pghost:pghost -i ubuntu /bin/bash


# GOKb

## Building the GOKb Docker Image (2018)

from ~/server/gokbg3

    docker login
    # Login using your credentials
    gradle buildImage
    # Follow the docker commands in build.gradle, but specifically
    docker push knowint/gokbg3


## Building the GOKb Docker Image (Legacy)

Build the gokb war file as per usual deployment 
    
    sdk use grails 2.5.4
    cd ~/gokb-phase-1/server/gokb
    grails prod war

    cd ../../docker
    ./docker-build.sh

## Configuring a gokb schema in pgsql

Wherever your DB is installed....

    postgres=# CREATE DATABASE gokb;
    CREATE DATABASE
    postgres=# CREATE USER knowint WITH PASSWORD 'knowint';
    CREATE ROLE
    postgres=# GRANT ALL PRIVILEGES ON DATABASE gokb to knowint;



## Run the gokb image

Some of these configurations need postgres to allow connections from addresses other than localhost.. Adding the following line to
/etc/postgresql/9.5/main/pg_hba.conf or your local equivalent will enable access from the 192.168 network. Do not do this lightly, the lines
are provided here for illustrative purposes - YMMV

host    all             all             192.168.0.0/16          md5


### Using a dockerized postgres and a dockerized tomcat

    docker run --link pghost:pghost -dit -p 8080:8080 gokb

### Using a postgres running on localhost

    DONT DO THIS - IT DOESN'T WORK WELL
    alias hostip="ip route show 0.0.0.0/0 | grep -Eo 'via \S+' | awk '{ print \$2 }'"
    docker run --add-host=pghost:$(hostip) -dit -p 8080:8080 gokb


### Using a postgres running elsewhere on the network

    docker run --add-host=pghost:address.of.pg.host -dit -p 8080:8080 gokb

### Dockerized postgres, local gokb

    install and autostart postgres as the top of this file
    edit /etc/hosts and add pghost as another name for localhost
    grails run-app


## Checking on the health of the GOKb installation



## Resource management

Docker may need more than the default resources, to allocate 4 cpus and 5G memory, use 
  docker ps 
  docker update -c 4 <DockerContainerId>
  docker update -m 6G <DockerContainerId>

