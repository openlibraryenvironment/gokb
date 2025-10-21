package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import org.gokb.UserProfileService
import org.gokb.cred.AllocatedReviewGroup
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.ReviewRequest
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import spock.lang.Specification
import spock.lang.Shared

@Integration
class ReviewsTestSpec extends AbstractAuthSpec {
  BlockingHttpClient http

  private ReviewRequest rr
  private ReviewRequest rrDeescalate
  private ReviewRequest rrAugment
  private JournalInstance title
  private JournalInstance matchedTitle
  private CuratoryGroup pkgGroup
  private CuratoryGroup titleGroup
  private CuratoryGroup editorialGroup
  private CuratoryGroup adminGroup
  private CuratoryGroup augmentGroup
  private User pkgGroupUser
  private User titleGroupUser
  private User editorialGroupUser
  private User augmentGroupUser
  private RefdataValue inProgress
  private RefdataValue inactive

  @Autowired
  UserProfileService userProfileService

  @Transactional
  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    if (!title) {
      title = JournalInstance.findOrSaveWhere(name: "testTitle for integration testing")
    }
    if (!matchedTitle) {
      matchedTitle = JournalInstance.findOrSaveWhere(name: "review matching title")
    }

    if (!rr) {
      rr = ReviewRequest.findOrSaveWhere(reviewRequest: "fake review request for integration testing", descriptionOfCause: "testCause", componentToReview: title)
    }

    if (!editorialGroup) {
      editorialGroup = CuratoryGroup.findByName("Journal Central Curators")
    }

    if (!adminGroup) {
      adminGroup = CuratoryGroup.findOrSaveWhere(name: "GOKB Team")
    }

    if (!augmentGroup) {
      augmentGroup = CuratoryGroup.findOrSaveWhere(name: "ZDB")
    }

    if (!titleGroup) {
      titleGroup = CuratoryGroup.findOrSaveWhere(name: "titleGroup")
    }

    if (!pkgGroup) {
      pkgGroup = CuratoryGroup.findOrSaveWhere(name: "pkgGroup", superordinatedGroup: titleGroup)
    }

    if (!inProgress) {
      inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
    }

    if (!inactive) {
      inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
    }

    if (!rrAugment) {
      rrAugment = ReviewRequest.findOrSaveWhere(reviewRequest: "test augment escalate", descriptionOfCause: "testCause", componentToReview: title)
      AllocatedReviewGroup.create(augmentGroup, rrAugment, true)
    }

    if (!rrDeescalate) {
      rrDeescalate = ReviewRequest.findOrSaveWhere(reviewRequest: "fake review request for deescalation testing", descriptionOfCause: "testCause", componentToReview: title)
      AllocatedReviewGroup.create(pkgGroup, rr, true)

      def argBase = AllocatedReviewGroup.create(pkgGroup, rrDeescalate, true)
      argBase.status = inactive
      argBase.save(flush: true)

      def argEsc = AllocatedReviewGroup.create(titleGroup, rrDeescalate, true)
      argEsc.escalatedFrom = argBase
      argEsc.save(flush: true)
    }

    Role contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    Role userRole = Role.findByAuthority('ROLE_USER')
    Role editorRole = Role.findByAuthority('ROLE_EDITOR')

    if (!User.findByUsername("pkgGroupUser")) {
      pkgGroupUser = new User(username: "pkgGroupUser", password: 'pkgGrp1', curatoryGroups: [pkgGroup], enabled: true, locked: false).save(flush: true)

      [contributorRole, userRole, editorRole].each { role ->
        if (!pkgGroupUser.authorities.contains(role)) {
            UserRole.create(pkgGroupUser, role)
        }
      }
    }

    if (!User.findByUsername("titleGroupUser")) {
      titleGroupUser = new User(username: "titleGroupUser", password: 'ttlGrp1', curatoryGroups: [titleGroup], enabled: true, locked: false).save(flush: true)

      [contributorRole, userRole, editorRole].each { role ->
        if (!titleGroupUser.authorities.contains(role)) {
            UserRole.create(titleGroupUser, role)
        }
      }
    }

    if (!User.findByUsername("augmentGroupUser")) {
      augmentGroupUser = new User(username: "augmentGroupUser", password: 'augmentGrp1', curatoryGroups: [augmentGroup], enabled: true, locked: false).save(flush: true)

      [contributorRole, userRole, editorRole].each { role ->
        if (!augmentGroupUser.authorities.contains(role)) {
            UserRole.create(augmentGroupUser, role)
        }
      }
    }
  }

  @Transactional
  def cleanup() {
  }

  void "test GET /rest/reviews with params"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() >= 1
  }

  void "test GET /rest/reviews/<id>"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/${rr.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().reviewRequest == rr.reviewRequest
  }

  void "test POST /rest/reviews"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      stdDesc: "Namespace Mismatch",
      reviewRequest: "POST Test Review",
      descriptionOfCause: "Created new test review",
      componentToReview: title.id,
      additionalInfo: [otherComponents: [[id: matchedTitle.id, name: matchedTitle.name, uuid: matchedTitle.uuid, oid: 'org.gokb.cred.JournalInstance:' + matchedTitle.id]]],
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.POST("$urlPath/rest/reviews", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    resp.body().reviewRequest == 'POST Test Review'
    resp.body().additionalInfo?.otherComponents?.size() == 1
    sleep(200)
  }

  void "test PUT /rest/reviews"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rr.id,
      reviewRequest: "Updated Test Review",
      descriptionOfCause: "Updated test review",
      additionalInfo: [otherComponents: [[id: matchedTitle.id, name: matchedTitle.name, uuid: matchedTitle.uuid, oid: 'org.gokb.cred.JournalInstance:' + matchedTitle.id]]],
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/${rr.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().reviewRequest == restBody.reviewRequest
    resp.body().additionalInfo?.otherComponents?.size() == 1
    resp.body().descriptionOfCause == restBody.descriptionOfCause
  }

  void "test check escalatable for package title review"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/escalatable/${rr.id}/${pkgGroup.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().isEscalatable == true
    resp.body().escalationTargetGroup?.id == titleGroup.id
  }

  void "test check escalatable for reference title review to admin group"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken('augmentGroupUser', 'augmentGrp1')
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/escalatable/${rrAugment.id}/${augmentGroup.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().isEscalatable == true
    resp.body().escalationTargetGroup?.id == adminGroup.id
  }

  void "test check escalatable for unescalatable title review"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken('admin', 'admin')
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/escalatable/${rrAugment.id}/${adminGroup.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().isEscalatable == false
    resp.body().escalationTargetGroup == null
  }

  void "test check escalatable title review with missing editing rights"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/escalatable/${rrAugment.id}/${augmentGroup.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().isEscalatable == false
    resp.body().escalationTargetGroup == null
    resp.body().errors.size() == 1
  }

  void "test successful review escalation"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rr.id,
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/escalate/${rr.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
  }

  void "test denied review escalation due to missing editing rights"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrAugment.id,
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/escalate/${rrAugment.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      resp = e.response
    }

    then:
    resp.status == HttpStatus.FORBIDDEN
  }

  void "test check deescalatable title review with missing editing rights"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.GET("$urlPath/rest/reviews/deescalatable/${rrDeescalate.id}/${augmentGroup.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().isDeescalatable == false
    resp.body().escalationTargetGroup == null
    resp.body().errors.size() == 1
  }


  void "test denied review deescalation due to invalid activeGroup"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrDeescalate.id,
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/deescalate/${rrDeescalate.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      resp = e.response
    }

    then:
    resp.status == HttpStatus.BAD_REQUEST
  }

  void "test denied review deescalation due to missing user editing rights"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrDeescalate.id,
      activeGroup: titleGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/deescalate/${rrDeescalate.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      resp = e.response
    }

    then:
    resp.status == HttpStatus.FORBIDDEN
  }

  void "test denied review deescalation due to invalid activeGroup"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrDeescalate.id,
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('titleGroupUser', 'ttlGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/deescalate/${rrDeescalate.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      resp = e.response
    }

    then:
    resp.status == HttpStatus.BAD_REQUEST
  }

  void "test successful review deescalation"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrDeescalate.id,
      activeGroup: titleGroup.id
    ]

    when:
    String accessToken = getAccessToken('titleGroupUser', 'ttlGrp1')
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/reviews/deescalate/${rrDeescalate.id}", restBody)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
  }


}
