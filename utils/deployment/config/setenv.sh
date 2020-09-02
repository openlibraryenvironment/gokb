#!/bin/sh
BASE=/home/hosting
export CATALINA_HOME="/opt/lib/apache-tomcat/latest"
export CATALINA_BASE="$BASE/gokb-tomcat"
export CATALINA_PID=$CATALINA_BASE/logs/catalina.pid

export JAVA_HOME="/opt/lib/jdk7/latest"
export M2_HOME="/opt/lib/apache-maven/latest"
export ANT_HOME="/opt/lib/apache-ant/latest"
export PATH="$PATH:$JAVA_HOME/bin:$M2_HOME/bin:$ANT_HOME/bin"
export CATALINA_OPTS="$CATALINA_OPTS -Dappserver.home=$CATALINA_HOME -Dappserver.base=$CATALINA_BASE"
export CATALINA_OPTS="$CATALINA_OPTS -Dcatalina.config=${CATALINA_BASE}/conf/catalina.properties"

export JAVA_OPTS="$JAVA_OPTS -server -Xms768m -Xmx4g -XX:MaxPermSize=512m"
