@Grapes([
    // @GrabResolver(name='central', root='http://central.maven.org/maven2/'),
    @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
    @Grab(group='org.slf4j', module='slf4j-api', version='1.7.6'),
    @Grab(group='org.slf4j', module='jcl-over-slf4j', version='1.7.6'),
    @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
    @Grab(group='xerces', module='xercesImpl', version='2.9.1'),
    @Grab(group='net.sf.opencsv', module='opencsv', version='2.0'),
    @Grab(group='commons-io', module='commons-io', version='2.5')
])

import groovyx.net.http.*
import static groovyx.net.http.ContentType.URLENC
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import static groovy.json.JsonOutput.*
import java.text.SimpleDateFormat
import java.io.*;
import au.com.bytecode.opencsv.CSVReader

def r = new CSVReader(new InputStreamReader(
                        new org.apache.commons.io.input.BOMInputStream(
                          new FileInputStream(args[0])),
                        java.nio.charset.Charset.forName('UTF-8')),
                      '\t' as char,
                      '\0' as char)

String [] nl;

int rownum = 0;

// Read column headings
nl = r.readNext()
println("Column heads: ${nl}");

while ((nl = r.readNext()) != null) {
  println("${rownum++}, ${nl.length}");
}
