package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration

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

@Integration
class ReviewsTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()
  private ReviewRequest rr
  private ReviewRequest rrDeescalate
  private JournalInstance title
  private JournalInstance matchedTitle
  private CuratoryGroup pkgGroup
  private CuratoryGroup titleGroup
  private CuratoryGroup editorialGroup
  private User pkgGroupUser
  private User titleGroupUser
  private User editorialGroupUser
  private RefdataValue inProgress
  private RefdataValue inactive

  @Autowired
  UserProfileService userProfileService

  @Transactional
  def setup() {
    title = JournalInstance.findOrCreateWhere(name: "testTitle for integration testing").save(flush: true, failOnError: true)
    matchedTitle = JournalInstance.findOrCreateWhere(name: "review matching title").save(flush: true, failOnError: true)
    rr = ReviewRequest.findOrCreateWhere(reviewRequest: "fake review request for integration testing", descriptionOfCause: "testCause", componentToReview:title)
    rr.save(flush: true, failOnError: true)

    Role contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    Role userRole = Role.findByAuthority('ROLE_USER')
    Role editorRole = Role.findByAuthority('ROLE_EDITOR')

    rrDeescalate = ReviewRequest.findOrCreateWhere(reviewRequest: "fake review request for deescalation testing", descriptionOfCause: "testCause", componentToReview:title)
    rrDeescalate.save(flush: true, failOnError: true)

    if (!editorialGroup) {
      editorialGroup = CuratoryGroup.findByName("Journal Central Curators")
    }

    if (!titleGroup) {
      titleGroup = CuratoryGroup.findOrCreateWhere(name: "titleGroup").save(flush: true, failOnError: true)
    }

    if (!pkgGroup) {
      pkgGroup = CuratoryGroup.findOrCreateWhere(name: "pkgGroup", superordinatedGroup: titleGroup).save(flush: true, failOnError: true)
      pkgGroup.superordinatedGroup = titleGroup
    }

    if (!inProgress) {
      inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
    }

    if (!inactive) {
      inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
    }

    AllocatedReviewGroup.create(pkgGroup, rr, true)
    def argBase = AllocatedReviewGroup.create(pkgGroup, rrDeescalate, true)
    argBase.status = inactive
    argBase.save(flush: true)

    def argEsc = AllocatedReviewGroup.create(titleGroup, rrDeescalate, true)
    argEsc.escalatedFrom = argBase
    argEsc.save(flush: true)

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
  }

  @Transactional
  def cleanup() {
    AllocatedReviewGroup.executeQuery("select id from AllocatedReviewGroup where escalatedFrom is not null").each {
      AllocatedReviewGroup.get(it).delete(flush:true)
    }

    AllocatedReviewGroup.executeUpdate("delete from AllocatedReviewGroup")

    ReviewRequest.executeQuery("select id from ReviewRequest").each {
      ReviewRequest.findById(it)?.expunge()
    }

    if (pkgGroupUser) userProfileService.delete(pkgGroupUser)
    if (titleGroupUser) userProfileService.delete(titleGroupUser)
    if (editorialGroupUser) userProfileService.delete(editorialGroupUser)

    JournalInstance.findById(title.id)?.expunge()
    JournalInstance.findById(matchedTitle.id)?.expunge()
    pkgGroup?.expunge()
    titleGroup?.expunge()
  }

  void "test GET /rest/reviews with params"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/reviews") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }

    then:
    resp.status == 200
    resp.json.data.size() >= 1
  }

  void "test GET /rest/reviews/<id>"() {
    given:
    def urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/reviews/${rr.id}") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }

    then:
    resp.status == 200
    resp.json.reviewRequest == rr.reviewRequest
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
    RestResponse resp = rest.post("$urlPath/rest/reviews") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(restBody as JSON)
    }

    then:
    resp.status == 201
    resp.json.reviewRequest == 'POST Test Review'
    resp.json.additionalInfo?.otherComponents?.size() == 1
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
    RestResponse resp = rest.put("$urlPath/rest/reviews/${rr.id}") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(restBody as JSON)
    }

    then:
    resp.status == 200
    resp.json.reviewRequest == restBody.reviewRequest
    resp.json.additionalInfo?.otherComponents?.size() == 1
    resp.json.descriptionOfCause == restBody.descriptionOfCause
  }

  void "test review escalation"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rr.id,
      activeGroup: pkgGroup.id
    ]

    when:
    String accessToken = getAccessToken('pkgGroupUser', 'pkgGrp1')
    RestResponse resp = rest.put("$urlPath/rest/reviews/escalate/${rr.id}") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(restBody as JSON)
    }

    then:
    resp.status == 200
  }

  void "test review deescalation"() {
    given:
    def urlPath = getUrlPath()
    def restBody = [
      id: rrDeescalate.id,
      activeGroup: titleGroup.id
    ]

    when:
    String accessToken = getAccessToken('titleGroupUser', 'ttlGrp1')
    RestResponse resp = rest.put("$urlPath/rest/reviews/deescalate/${rrDeescalate.id}") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(restBody as JSON)
    }

    then:
    resp.status == 200
  }
}
