
Individual properties can be overridden when running the jar with -- settings, for example::

    java -jar ./build/libs/gokbg3-8.0.0.jar --gokb.es.cluster=gokbg3


war settings can be overridden by adding individual Parameter elements 
or overall config file settings
to the context file which lives in TOMCAT_HOME/conf/Catalina/localhost/gokbg3.xml for example


    <Context path="/kbplus7" reloadable="false">
      <Environment name="spring.config.location" value="/home/gokbg3/gokbg3.yaml" type="java.lang.String" />
      <Parameter name="gokb.es.cluster" value="gokbg3" override="false"/>
    </Context>


