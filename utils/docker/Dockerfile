FROM ubuntu:16.04
MAINTAINER Ian Ibbotson <ian.ibbotson@k-int.co.uk>
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
RUN apt-get update
RUN apt-get dist-upgrade -y
# RUN locale-gen en_US en_US.UTF-8
RUN apt-get install -y python-software-properties software-properties-common curl wget
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer
RUN apt-get install -y libapr1 supervisor openssh-server 
RUN mkdir -p /var/log/supervisor
RUN rm -rf /var/lib/apt/lists/*
RUN mkdir -p "$CATALINA_HOME"
WORKDIR $CATALINA_HOME
RUN wget http://www.mirrorservice.org/sites/ftp.apache.org/tomcat/tomcat-8/v8.5.4/bin/apache-tomcat-8.5.4.tar.gz
RUN tar xzvf ./apache-tomcat-8.5.4.tar.gz --strip 1
RUN rm ./apache-tomcat-8.5.4.tar.gz
RUN pwd
RUN ls
COPY ./gokb-7.0.11.war ./webapps/gokb.war
EXPOSE 8080 22
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf
CMD ["/usr/bin/supervisord"]
# CMD /bin/bash
# CMD ["service tomcat8 start && tail -f /var/log/tomcat8/catalina.out"]
# CMD ["java", "-jar", "./first-module.jar", "1>server.log", "2>server.log"]
