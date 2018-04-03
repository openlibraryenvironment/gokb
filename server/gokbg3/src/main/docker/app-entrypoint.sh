#!/bin/sh
java -Djava.security.egd=file:/dev/./urandom -jar /app/application.jar \
	--gokb.es.cluster=$GOKB_ES_CLUSTER \
	--gokb.defaultCuratoryGroup=${GOKB_DEFAULT_CG} \
	--dataSource.url=${GOKB_DB} \
	--dataSource.username=${GOKB_DB_USER} \
	--dataSource.password=${GOKB_DB_PASS}
