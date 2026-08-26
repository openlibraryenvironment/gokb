package org.gokb

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source

// @Transactional
class WekbAPIService {
  static transactional = false
  def grailsApplication
  OrgService orgService
  static BlockingHttpClient blockingHttpClient
  static final String BASE_URL = "https://wekb.hbz-nrw.de"
  static final String PATH = "/api2/searchApi"
  String USERNAME
  String PASSWORD


  @javax.annotation.PostConstruct
  def init() {
    USERNAME = grailsApplication.config.getProperty('gokb.wekbApiUser')
    PASSWORD = grailsApplication.config.getProperty('gokb.wekbApiUserPassword')
    blockingHttpClient = HttpClient.create(new URL(BASE_URL)).toBlocking()
  }

  public List getPackageByUuid(String uuid) {
    Map params = [
      componentType: "package",
      uuid: uuid
    ]
    List result = []

    if (isUuidValid(uuid)) {
      result = executeRequest (params)
    }

    return result
  }

  public List getPlatformByUuid (String uuid) {
    Map params = [
      componentType: "platform",
      uuid: uuid
    ]
    List result = []

    if (isUuidValid(uuid)) {
        result = executeRequest (params)
    }

    return result
  }

  public List getProviderByUuid (String uuid) {
    Map params = [
      componentType: "provider",
      uuid: uuid
    ]
    List result = []

    if (isUuidValid(uuid)) {
      result = executeRequest (params)
    }

    return result
  }

  public List getTIPPSOfPackage (String uuid, Integer max, Integer offset) {
    Map params = [
      componentType: "TitleInstancePackagePlatform",
      tippPackageUuid: uuid,
      max: max,
      offset: offset,
      sort: "dateCreated"
    ]
    List result = []

    if (isUuidValid(uuid)) {
      result = executeRequest (params)
    }

    return result
  }

  private List executeRequest (Map queryParams) {
    List result = []

    try {
      UriBuilder basePath = UriBuilder.of(BASE_URL).path(PATH)

      for ( def param : queryParams.entrySet() ) {
        basePath = basePath.queryParam(param.getKey().toString(), param.getValue())
      }

      URI uri = basePath.queryParam('username', USERNAME)
              .queryParam('password', PASSWORD)
              .build()

      HttpResponse resp = blockingHttpClient.exchange(HttpRequest.POST(uri, []), String)

      if (resp.status == HttpStatus.OK) {
        Object jsonResponse = new JsonSlurper().parseText(resp.body())

        if (jsonResponse?.result_count_total > 0 || jsonResponse?.result?.size() > 0) {
          result = jsonResponse.result
        }

        log.debug(jsonResponse.toString())
      }
    } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
      log.error("HttpClientException...", e)
    }
    catch (Exception e) {
      log.error("Exception...", e)
    }

    return result
  }

  private static boolean isUuidValid(String uuid){
    //  ^\{?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\}?$
    if ( uuid ) {
      String[] parts = uuid.split("-")

      if (parts.length == 5 &&
          parts[0].length() == 8 &&
          parts[1].length() == 4 &&
          parts[2].length() == 4 &&
          parts[3].length() == 4 &&
          parts[4].length() == 12
      ) {
        return true
      }
    }

    return false
  }

  public Map checkIfProviderExists (String uuid) {
    Map result = [:]
    Org wekbProvider = getProviderByUuid(uuid)

    log.debug("WEKBPROVIDER: " + wekbProvider)
    String providerHomepage = null
    String providerName = null

    if (wekbProvider) {
      providerName = wekbProvider.name
      providerHomepage = wekbProvider.homepage

      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      List existingProvider = Org.findAllByNameIlikeAndStatusNotEqual(providerName, status_deleted)

      if (existingProvider.size() > 0) {
        result.providerExists = true
        result.providerId = existingProvider.get(0).id

        log.debug("***** EXISTING PROVIDER ****** " + existingProvider.get(0))

      } else {
        result.providerExists = false
      }
      // Homepage soll bei Neuanlage auch gespeichert werden
      result.providerHomepage = providerHomepage
    }

    return result
  }
}
