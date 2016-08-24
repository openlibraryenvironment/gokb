FROM ubuntu:16.04
MAINTAINER Ian Ibbotson <ian.ibbotson@k-int.co.uk>
RUN apt-get update
RUN apt-get dist-upgrade -y
# RUN locale-gen en_US en_US.UTF-8
RUN apt-get install -y python-software-properties software-properties-common curl wget
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer
RUN apt-get install -y tomcat8 libapr1


RUN rm -rf /var/lib/apt/lists/*
# COPY /build/libs/first-module.jar .
# EXPOSE 9201
# CMD ["java", "-jar", "./first-module.jar", "1>server.log", "2>server.log"]
