
The old grails ~/.grails config mechanism is deprecated. Override defaults in your local config by
creating a file called something like /home/ianibbo/localconfig/gokbdev.yaml

and then running

java -jar JAR.jar --spring.config.location=file:/home/ianibbo/localconfig/gokbdev.yaml

grails -Dspring.config.location=file:/home/ianibbo/localconfig/gokbdev.yaml run-app



For example, to override the ES cluster name, put this in gokbdev.yaml


gokb:
  es:
    cluster: kbplusg3

