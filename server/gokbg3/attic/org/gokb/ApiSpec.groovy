package org.gokb

import spock.lang.Specification
import org.gokb.cred.*
// For @Autowired
import org.springframework.beans.factory.annotation.*

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import spock.lang.*
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;

import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.mock.web.MockMultipartHttpServletRequest


/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@Integration
class ApiSpec extends Specification {

    // Stop grails from rolling back the transaction at the end of each call
    static transactional = false

    @Autowired
    WebApplicationContext ctx

    def tsv_data = '''id	expired	locked	enabled	password	expired	username	display_name	email	default_page_size	show_info_icon	show_quick_view	kbc_name
1	0	0	1	9e63c7e75e9a7b37a5fdecafd4db193e5e8c8687bf57ea87dbbd2a9cb214104c	0	flintstone	flintstone		10	Yes	Yes	
'''


    // extending IntegrationSpec means this works
    @Autowired
    TitleLookupService titleLookupService

    def setup() {
    }

    def cleanup() {
    }

    void "Bulk Load Users"() {
      ApiController controller = new ApiController()
      given: "A file of users to create"
        def mockRequest = new MockMultipartHttpServletRequest()
        controller.metaClass.request = mockRequest
        def file = new MockMultipartFile('users', 'users.tsv', 'text/tsv', new ByteArrayInputStream(tsv_data.getBytes()))
        controller.request.addFile(file)
      when: "user asks for file to be loaded"
        controller.bulkLoadUsers()
      then: "System ingests and creates the named accounts"
        User.list().size()>2  // Bootstrap creates admin and ingest users
      expect: "We can locate the user with username flintstone"
        User.findAllByUsername('flintstone').size() == 1
    }


}
