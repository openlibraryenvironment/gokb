@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])



import javax.mail.*
import javax.mail.search.*
import java.util.Properties
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper

import com.gargoylesoftware.htmlunit.*

println "Hello";

config = null;
cfg_file = new File('./ingest-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}

println("Using config ${config}");
println("Pulling latest messages");

update(config)


def test(){
  println "archivo [${args[0]}]" 
  File f = new File(args[0])
  f.each{
    String name =it.substring( it.lastIndexOf("/")+1)+".wsdl"
    println" saving [$name] "
    String cmd ="wget ${it}?wsdl -O ${name}"
    println cmd 
    cmd.execute().text
  }
}
