dataSource.url="jdbc:mysql://<%= @dshost %>/gokb?autoReconnect=true&amp;characterEncoding=utf8"
dataSource.username="gokb"
dataSource.password="<%= @dspass %>"
kuali.analytics.code="UA-44604466-1"
grails.plugins.springsecurity.ui.forgotPassword.emailFrom='test-gokb@kuali.org'
grails.plugins.springsecurity.ui.register.emailFrom = 'test-gokb@kuali.org'
grails.plugins.springsecurity.ui.forgotPassword.emailSubject = 'Test GoKB Forgotten Password'
logSql = true
formatSql = true
sysid='<%= @gokbsysid %>'
gokb.es.cluster='elasticsearch'
serverUrl='https://<%= @gokbhost %>/gokb'

