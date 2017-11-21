#!/usr/bin/groovy

@Grapes([
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.0'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.0'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @Grab(group='info.freelibrary', module='freelib-marc4j', version='2.6.3')
])


import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*
import org.apache.log4j.*
import java.text.SimpleDateFormat
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.Record;


def target_service = new HTTPBuilder(args[0])


def is = new FileInputStream(args[0])

MarcReader reader = new org.marc4j.MarcStreamReader(is);
while (reader.hasNext()) {
  Record record = reader.next();
  System.out.println(record.toString());
}    


println("Done");
