include apt

exec {
  'set-licence-selected':
  command => '/bin/echo debconf shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections';
 
  'set-licence-seen':
  command => '/bin/echo debconf shared/accepted-oracle-license-v1-1 seen true | /usr/bin/debconf-set-selections';
}

apt::ppa { 'ppa:webupd8team/java': 
}

package { "oracle-java7-installer":
  ensure => installed,
  require  => [ Apt::Ppa['ppa:webupd8team/java'], Exec['set-licence-selected'], Exec['set-licence-seen'] ]
}

package { "oracle-java7-set-default":
  ensure => installed,
  require  => Package['oracle-java7-installer'],
}


package { "apache2": ensure => installed, }

package { "libapache2-mod-shib2": ensure => installed, }

package { "tomcat7-user": ensure => installed, }

package { "libshibsp6": ensure => installed, }

package { "libtcnative-1": ensure => installed, require => Package['tomcat7-user'],}

user { "hostingUser" :
  name => hosting,
  ensure => present,
  home => '/home/hosting',
  managehome => true
}

exec { 'tcsetup':
      command => 'tomcat7-instance-create tomcat-gokb',
      creates => '/home/hosting/tomcat-gokb',
      cwd => '/home/hosting',
      logoutput => true,
      path => '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
      user => hosting,
      require => [ User['hostingUser'], Package['tomcat7-user'], Package['libtcnative-1'] ]
}

exec { 'activateAPR':
      command => 'vi /home/hosting/tomcat-gokb/conf/server.xml -c ":g/org.apache.catalina.core.AprLifecycleListener/normal ddp" -c ":wq"',
      path => '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
      user => hosting,
      require => [ Exec['tcsetup'] ],
      returns => [ 1 ]
}
