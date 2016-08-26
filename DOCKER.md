
This document details one approach to running GOKb in a docker environment.

It is focussed on the PostgreSQL oriented deployment, and is docker-centric in it's approach.


# PostgreSQL Instance

The standard docker postgres instance is sufficent for a starter installation. The following command will download the postgres image and then start the image. The container will auto start with docker (So always be available), will be named pghost, sets the default postgres password and exposes the postgres http port. The image runs as a daemon (-d). Note also, that the GOKb default datasource config looks for a postgres host called pghost. The instance is so named so these things align. If you choose a different name for your postgres host, or have postgres running on another host and don't wish to use this mechanism, you will need to us --add-host to add the pghost hostname to your gokb container.

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

You can start a vanulla ubuntu with the following command

    docker run -t --link pghost:pghost -i ubuntu /bin/bash


# GOKb

## Building the GOKb Docker Image

Build the gokb war file as per usual deployment 
    
    sdk use grails 2.5.4
    cd ~/gokb-phase-1/server/gokb
    grails prod war

    cp target/gokb-7.0.11.war ../../docker
    cd ../../docker
    docker build -t gokb .

## Run the gokb image

    docker run --link pghost:pghost -dit -p 8080:8080 gokb



