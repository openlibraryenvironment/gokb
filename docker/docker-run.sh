#!/bin/bash
# docker run -d -p 9201:9201 first-module
# -p hostPort:containerPort
# Run docker, detached, map port 8080 on localhost to 8080 on the container
# Using --add-host postgres:nnn.nnn.nnn.nnn
# add --restart=always to restart always
# Handy Alias

#
# This seems to get default route, not host IP
#
# alias hostip="ip route show 0.0.0.0/0 | grep -Eo 'via \S+' | awk '{ print \$2 }'"
#


# docker run --add-host=pghost:$(hostip) -dit -p 8080:8080 gokb
docker run --add-host dockerhost:`/sbin/ip route|awk '/default/ { print  $3}'` -dit -p 8080:8080 gokb

echo Running docker ps -a
docker ps -a

echo attach with docker attach ID
echo detach without killing with ctrl-p ctrl-q
