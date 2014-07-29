package org.gokb.client

import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

public class Sync {

  public doSync(host, notificationTarget) {
    println("Get latest changes");

    def http = new HTTPBuilder( host )

    def more = true
    println("Attempt get...");
    def resumption=null

    // perform a GET request, expecting JSON response data
    while ( more ) {
      println("org.gokb.client.Sync::doSync - Make http request..");

      http.request( GET, XML ) {
        uri.path = '/gokb/oai/packages'
        if ( resumption ) {
          uri.query = [ verb:'ListRecords', resumptionToken: resumption ]
        }
        else {
          uri.query = [ verb:'ListRecords', metadataPrefix: 'gokb' ]
        }
  
        // response handler for a success response code:
        response.success = { resp, xml ->
          println resp.statusLine
  
          xml.'ListRecords'.'record'.each { r ->
            // println("Record id...${r.'header'.'identifier'}");
            // println("Package Name: ${r.metadata.package.packageName}");
            // println("Package Id: ${r.metadata.package.packageId}");

            GokbPackageDTO dto = new GokbPackageDTO()
            dto.packageId=r.metadata.package.packageId.text();
            dto.packageName=r.metadata.package.packageName.text();

            r.metadata.package.packageTitles.TIP.each { pt ->
              // println("Title: ${pt.title}");
              def new_tipp_dto = new GokbTippDTO();
              new_tipp_dto.tippId='';
              new_tipp_dto.pkgId='';
              new_tipp_dto.pkgName='';
              new_tipp_dto.platId='';
              new_tipp_dto.platName='';
              new_tipp_dto.title=pt.title.text();
              new_tipp_dto.titleId=pt.titleId.text();
              new_tipp_dto.startVolume='';
              new_tipp_dto.startIssue='';
              new_tipp_dto.startDate='';
              new_tipp_dto.endVolume='';
              new_tipp_dto.endIssue='';
              new_tipp_dto.endDate='';
              new_tipp_dto.coverageDepth='';
              new_tipp_dto.coverageNote='';
              dto.tipps.add(new_tipp_dto);
            }

            notificationTarget.notifyChange(dto)
          }

          if ( xml.'ListRecords'.'resumptionToken'.size() == 1 ) {
            resumption=xml.'ListRecords'.'resumptionToken'.text()
            println("Iterate with resumption : ${resumption}");
          }
          else {
            more = false
          }
        }

        // handler for any failure status code:
        response.failure = { resp ->
          println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
          more = false
        }
      }
      println("Endloop");
    }

    println("All done");
  }

}
