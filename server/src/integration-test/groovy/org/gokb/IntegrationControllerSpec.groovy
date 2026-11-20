package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import groovy.json.JsonSlurper

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

import org.gokb.cred.*
import org.gokb.TitleLookupService
import org.springframework.beans.factory.annotation.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.context.WebApplicationContext

import spock.lang.Shared
import spock.lang.Specification

@Integration
@Rollback
class IntegrationControllerSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  // extending IntegrationSpec means this works
  @Autowired
  TitleLookupService titleLookupService


  BlockingHttpClient client

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path', String) ?: ''}".toString()
  }

  def setup() {

    if (!client) {
      client = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    CuratoryGroup new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true, failOnError: true)
    Org acs_org = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true, failOnError: true)

    JournalInstance history_title = JournalInstance.findByName('IntegrationHistoryTitle')

    if (!history_title) {
      IdentifierNamespace ns_eissn = IdentifierNamespace.findByValue('eissn')
      Identifier history_issn = Identifier.findByValue('2345-2331') ?: new Identifier(value: '2345-2331', namespace: ns_eissn).save(flush:true)
      history_title = new JournalInstance(name: 'IntegrationHistoryTitle').save(flush: true, failOnError: true)

      history_title.addIdentifier(history_issn)
    }
  }

  def cleanup() {
    CuratoryGroup.findByName('TestGroup1')?.expunge()
    Org.findByName("American Chemical Society")?.expunge()
    Org.findByName('TestOrgAcs')?.expunge()
    Platform.findByName('TestPlt1')?.expunge()

    TitleInstance.list().each { title ->
      title.expunge()
    }
  }

  void "Test assertGroup"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        name: "TestGroup2",
        owner: "admin"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/assertGroup", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created up as it does not already exist"
    resp.body()?.groupId != null
    resp.body()?.message == 'Created new group TestGroup2!'
    resp.body()?.result == 'OK'
    expect: "Find item by name only returns one item"
    def matching_groups = CuratoryGroup.executeQuery('select cg from CuratoryGroup as cg where cg.name = :n', [n: json_record.name]);
    matching_groups.size() == 1
    matching_groups[0].id == resp.body()?.groupId
  }

  void "Test assertOrg :: Import new Org"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "dbpedia",
          value: "org-test-id-acs"
        ]
      ],
      name: "TestOrgAcs"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/assertOrg", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created up as it does not already exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by name only returns one item"
    sleep(200)
    def matching_orgs = Org.executeQuery('select o from Org as o where o.name = :n', [n: json_record.name])
    matching_orgs.size() == 1
    matching_orgs[0].id == resp.body()?.orgId
    matching_orgs[0].ids?.size() == 1
  }

  void "Test crossReferencePlatform :: Import new Platform"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      platformName: "TestPlt1",
      name: "TestPlt1",
      platformUrl: "https://acstest.url"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePlatform", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item was created"
    resp.body()?.message != null
    resp.body()?.result == "OK"
    expect: "Find item by name only returns one item"
    sleep(500)
    List matching_platforms = Platform.executeQuery('select p from Platform as p where p.name = :n', [n: json_record.platformName])
    matching_platforms.size() == 1
    matching_platforms[0].id == resp.body()?.platformId
  }

  void "Test crossReferenceTitle JOURNAL Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "zdb",
          value: "1483109-0"
        ],
        [
          type: "eissn",
          value: "1520-5118"
        ],
        [
          type: "issn",
          value: "1021-8564"
        ]
      ],
      name: "Journal of agricultural and food chemistry",
      publishedFrom: "1953-01-01 00:00:00.000",
      publishedTo: "",
      publisher: [
        [
          name: "American Chemical Society",
          _linkStart: "1953-01-01 00:00:00.000"
        ]
      ],
      type: "Serial"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item"
    List ids = [['ns': 'issn', 'value': '1021-8564']]
    List matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
    matching_with_class_one_ids[0] == resp.body()?.titleId
  }

  void "Test crossReferenceTitle with incomplete dates"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "zdb",
          value: "2477443-1"
        ]
      ],
      name: "TestJournal_Dates",
      publishedFrom: "1953-01",
      publishedTo: "2001",
      publisher: [
        [
          name: "American Chemical Society",
          _linkStart: "1953-01-01"
        ]
      ],
      type: "Serial"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item"
    sleep(250)
    TitleInstance title = TitleInstance.findById(resp.body()?.titleId)
    title != null
    title.publishedFrom?.toString() == "1953-01-01 00:00:00.0"
    title.publishedTo?.toString() == "2001-12-31 00:00:00.0"
    title.publisherLinks.size() == 1
    title.publisherLinks[0].startDate?.toString() == "1953-01-01"
  }

  void "Test crossReferenceTitle :: Journal with history containing new title"() {
    given:
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "eissn",
          value: "1549-960X"
        ],
        [
          type: "zdb",
          value: "1491237-5"
        ]
      ],
      name: "Journal of chemical information and modeling",
      publishedFrom: "1982-01-01",
      publishedTo: "",
      historyEvents: [
        [
          date: "1982-01-01",
          from: [
              [
                title: "Journal of chemical documentation",
                identifiers: [
                  [
                    type: "eissn",
                    value: "1541-5732"
                  ],
                  [
                    type: "zdb",
                    value: "2096906-5"
                  ]
                ]
              ]
          ],
          to: [
            [
              title: "Journal of chemical information and modeling",
              identifiers: [
                [
                  type: "eissn",
                  value: "1549-960X"
                ],
                [
                  type: "zdb",
                  value: "1491237-5"
                ]
              ]
            ]
          ]
        ]
      ],
      publisher: [
        [
          name: "American Chemical Society",
          _linkStart: "1982-01-01",
        ]
      ],
      type: "Serial"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "The history event is not created because one of the titles does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Missing title is not created"
    JournalInstance main_ti = JournalInstance.findByName("Journal of chemical information and modeling")
    main_ti.titleHistory.size() == 0
  }

  void "Test crossReferenceTitle :: Journal with history containing two existing titles"() {
    given:
    JournalInstance history_title = JournalInstance.findByName('IntegrationHistoryTitle')

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "eissn",
          value: "1549-960X"
        ],
        [
          type: "zdb",
          value: "1491237-5"
        ]
      ],
      name: "Journal of chemical information and modeling",
      publishedFrom: "1982-01-01",
      publishedTo: "",
      historyEvents: [
        [
          date: "1982-01-01",
          from: [
              [
                title: "Journal of chemical documentation", // title discrepancy should not matter
                identifiers: [
                  [
                    type: "eissn",
                    value: "2345-2331"
                  ]
                ]
              ]
          ],
          to: [
            [
              title: "Journal of chemical information and modeling",
              identifiers: [
                [
                  type: "eissn",
                  value: "1549-960X"
                ],
                [
                  type: "zdb",
                  value: "1491237-5"
                ]
              ]
            ]
          ]
        ]
      ],
      publisher: [
        [
          name: "American Chemical Society",
          _linkStart: "1982-01-01",
        ]
      ],
      type: "Serial"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "The title is created with a history event"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "New title has one history event"
    JournalInstance main_ti = JournalInstance.findByName("Journal of chemical information and modeling")
    main_ti.titleHistory.size() == 1
  }

  void "Test crossReferenceTitle BOOK Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12232-23-5"
        ],
        [
          type: "doi",
          value: "10.1515/pdtc"
        ]
      ],
      name: "Test Book 1",
      type: "monograph",
      editionNumber: "4",
      volumeNumber: "3",
      firstAuthor: "J. Smith",
      dateFirstOnline: "2019-01-01 00:00:00.000"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    List ids = [['ns': 'isbn', 'value': '978-13-12232-23-5']]
    TitleInstance obj = TitleInstance.get(resp.body()?.titleId)
    obj?.ids?.collect { it.value == ids[0].value }
  }

  void "Test crossReferenceTitle identifier lock"() {
    when: "Caller asks for this record to be cross referenced"
    List json_record = [
      [
        identifiers: [
          [
            type: "isbn",
            value: "978-13-12232-24-2"
          ]
        ],
        type: "Monograph",
        name: "Test Book 1",
        editionNumber: "4",
        volumeNumber: "3",
        firstAuthor: "J. Smith",
        dateFirstOnline: "2019-01-01 00:00:00.000"
      ],
      [
        identifiers: [
          [
            type: "isbn",
            value: "978-13-12232-24-2"
          ]
        ],
        name: "Test Book 1",
        type: "Monograph",
        editionNumber: "4",
        volumeNumber: "3",
        firstAuthor: "J. Smith",
        dateFirstOnline: "2019-01-01 00:00:00.000"
      ]
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.results?.size() == 2
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    List ids = [['ns': 'isbn', 'value': '978-13-12232-24-2']]
    resp.body()?.results[0].titleId == resp.body()?.results[1].titleId
  }

  void "Test crossReferenceTitle with duplicate identifier"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12232-25-9"
        ],
        [
          type: "isbn",
          value: "978-13-12232-25-9"
        ]
      ],
      type: "Monograph",
      name: "Test Book 1",
      editionNumber: "4",
      volumeNumber: "3",
      firstAuthor: "J. Smith",
      dateFirstOnline: "2019-01-01 00:00:00.000"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    List ids = [['ns': 'isbn', 'value': '978-13-12232-25-9']]
    IdentifierNamespace ns = IdentifierNamespace.findByValueIlike('isbn')
    List matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
  }

  void "Test crossReferenceTitle with wrong type"() {
    when: "Caller asks for this record to be cross referenced"
    Map book_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12324-23-8"
        ]
      ],
      name: "Test Book Missing Type"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", book_record).basicAuth('admin', 'admin')
    HttpResponse resp = client.exchange(request, Map)

    then: "Item is rejected"
    resp.body()?.result == 'ERROR'
  }

  void "Update Title remove VariantName via fullsync"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12232-26-6"
        ]
      ],
      variantNames: [
        "TestVariantBookName"
      ],
      type: "Monograph",
      name: "Test Book 1",
      editionNumber: "4",
      volumeNumber: "3",
      firstAuthor: "J. Smith",
      dateFirstOnline: "2019-01-01 00:00:00.000"
    ]

    Map json_update_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12232-26-6"
        ]
      ],
      type: "Monograph",
      name: "Test Book 1",
      editionNumber: "4",
      volumeNumber: "3",
      firstAuthor: "J. Smith",
      dateFirstOnline: "2019-01-01 00:00:00.000"
    ]


    HttpRequest request1 = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp1 =  client.exchange(request1, Map)
    HttpRequest request2 = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle?fullsync=true", json_update_record).basicAuth('admin', 'admin')

    HttpResponse resp2 =  client.exchange(request2, Map)

    then: "Item is created in the database"
    resp1.body().result == 'OK'
    resp2.body().result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    resp1.body()?.titleId == resp2.body()?.titleId
    BookInstance bookInstance = BookInstance.get(resp2.body()?.titleId)
    bookInstance?.variantNames?.size() == 0
  }

  void "Create Title with problematic characters"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
      identifiers: [
        [
          type: "isbn",
          value: "978-13-12112-23-0"
        ]
      ],
      type: "Monograph",
      name: "TestVariantBookName \"Quotes Test\"",
      editionNumber: "4",
      volumeNumber: "3",
      firstAuthor: "J. Smith",
      dateFirstOnline: "2019-01-01 00:00:00.000"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    HttpResponse resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    BookInstance bookInstance = BookInstance.get(resp.body()?.titleId)
    bookInstance?.name == 'TestVariantBookName "Quotes Test"'
  }
}
