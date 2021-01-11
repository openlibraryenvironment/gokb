#!/bin/bash
# docker run -d -p 9201:9201 first-module
# -p hostPort:containerPort
# Run docker, detached, map port 8080 on localhost to 8080 on the container
# Using --add-host postgres:nnn.nnn.nnn.nnn
# add --restart=always to restart always
# Handy Alias

echo Start gokb container using containerized postgresql mapped as pghost. Host is temporary, vanishes when done.
docker run -t --link pghost:pghost -p 8080:8080 gokb

echo Running docker ps -a
docker ps -a

echo attach with docker attach ID
echo detach without killing with ctrl-p ctrl-q
