package org.gokb

import grails.gorm.transactions.Transactional
import org.gokb.cred.User
import org.gokb.cred.WebHookEndpoint

import java.util.regex.Pattern

@Transactional
class WebEndpointService {
  def restMappingService
  def componentLookupService

  public Map lookupWebendpoints(User user, params){
    Map result = [:]
    int max = params.limit ? params.long('limit') : 10
    int offset = params.offset ? params.long('offset') : 0

    List webEndpoints = []
    String query = 'select whe from WebHookEndpoint whe '
    String countQuery = 'select count(whe.id) from WebHookEndpoint whe '
    String whereClause = 'where whe.url like :method'
    Map queryParams = [:]


    if (params['method']) {
      query += whereClause
      countQuery += whereClause
      queryParams['method'] = params['method'].toString().trim().toLowerCase().equals("ftp") ? 'ftp%' : 'http%'
    }

    webEndpoints = WebHookEndpoint.executeQuery(query, queryParams, [max: max, offset: offset, readOnly: true])
    result.data = []
    webEndpoints.each { we ->
      result.data << restMappingService.mapObjectToJson(we, params, user)
    }

    int count = WebHookEndpoint.executeQuery(countQuery, queryParams, [:])[0]

    result['_pagination'] = [
      offset: offset,
      limit: max,
      total: count
    ]

    result = componentLookupService.generateLinks(result, WebHookEndpoint, null, params, max, offset, count)

    return result
  }

  public Map extractFtpUrlParts (String webEndpointUrl, String sourceUrl) {
    Map result = [:]
    String protocol = ""

    if (webEndpointUrl.startsWith("ftp://")){
      protocol = "ftp://"
    }
    else if (webEndpointUrl.startsWith("ftps://")) {
      protocol = "ftps://"
    }

    //we dont need the protocol
    String hostname = webEndpointUrl.replace(protocol, "")
    String filename = ""
    String directory = "/"
    String completeUrl = ""

    // leading and trailing slashes that could be part of host or filename
    // are set in the directory part
    if (hostname?.contains("/")) {
      String[] parts = hostname.split("/")
      hostname = parts[0]

      for (int i = 1; i < parts.length; i++) {
        directory = directory.concat(parts[i] + "/")
      }
    }

    if (sourceUrl?.startsWith("/")) {
      sourceUrl = sourceUrl.substring(1)
    }

    if (sourceUrl?.contains("/")) {
      String[] parts = sourceUrl.split("/")
      filename = parts[parts.length - 1]
      directory = directory + sourceUrl.substring(0, sourceUrl.lastIndexOf("/") + 1)
    }
    else {
      filename = sourceUrl
    }

    completeUrl = protocol + hostname + directory + filename

    result.hostname = hostname
    result.filename = filename
    result.directory = directory
    result.complete = completeUrl

    result

  }
}
