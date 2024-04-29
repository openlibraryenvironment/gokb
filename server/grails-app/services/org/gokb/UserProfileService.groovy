package org.gokb


import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.gsp.PageRenderer
import org.apache.commons.validator.routines.EmailValidator
import org.gokb.cred.*
import org.gokb.refine.RefineProject
import org.springframework.beans.factory.annotation.Autowired

@Transactional
class UserProfileService {

  def grailsApplication
  def passwordEncoder
  def mailService
  def messageSource
  PageRenderer groovyPageRenderer

	static final String EMAIL_LAYOUT = "/layouts/email"
  static final String ACTIVATION_NOTICE_TEMPLATE = "/register/_activationNoticeMail"

  def delete(User user_to_delete) {
    def result = [:]
    log.debug("Deleting user ${user_to_delete.id} ..")
    def del_user = User.findByUsername('deleted')

    if (user_to_delete && del_user) {
      log.debug("Replacing links to user with placeholder ..")
      ReviewRequest.executeUpdate("update ReviewRequest set raisedBy = :del where raisedBy = :utd", [utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set closedBy = :del where closedBy = :utd", [utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set reviewedBy = :del where reviewedBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set createdBy = :del where createdBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set modifiedBy = :del where modifiedBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set lastCheckedOutBy = :del where lastCheckedOutBy = :utd", [utd: user_to_delete, del: del_user])
      ReviewRequestAllocationLog.executeUpdate("update ReviewRequestAllocationLog set allocatedTo = :del where allocatedTo = :utd", [utd: user_to_delete, del: del_user])
      Folder.executeUpdate("update Folder set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])
      CuratoryGroup.executeUpdate("update CuratoryGroup set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])
      Note.executeUpdate("update Note set creator = :del where creator = :utd", [utd: user_to_delete, del: del_user])
      KBComponent.executeUpdate("update KBComponent set lastUpdatedBy = :del where lastUpdatedBy = :utd", [utd: user_to_delete, del: del_user])
      UserOrganisation.executeUpdate("update UserOrganisation set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])

      log.debug("Setting links to null ..")
      ReviewRequest.executeUpdate("update ReviewRequest set allocatedTo = null where allocatedTo = :utd", [utd: user_to_delete])
      WebHookEndpoint.executeUpdate("update WebHookEndpoint set owner = null where owner = :utd", [utd: user_to_delete])
      Package.executeUpdate("update Package set userListVerifier = null where userListVerifier = :utd", [utd: user_to_delete])
      // Better to abort deletion?
      BulkImportListConfig.executeUpdate("update BulkImportListConfig set owner = null where owner = :utd", [utd: user_to_delete])

      log.debug("Deleting dependent entities ..")
      DSAppliedCriterion.executeUpdate("delete from DSAppliedCriterion where user = :utd", [utd: user_to_delete])
      ComponentLike.executeUpdate("delete from ComponentLike where user = :utd", [utd: user_to_delete])
      History.executeUpdate("delete from History where owner = :utd", [utd: user_to_delete])
      UserOrganisationMembership.executeUpdate("delete from UserOrganisationMembership where party = :utd", [utd: user_to_delete])
      SavedSearch.executeUpdate("delete from SavedSearch where owner = :utd", [utd: user_to_delete])
      UserRole.removeAll(user_to_delete)

      log.debug("Deleting user object ..")
      user_to_delete.delete(flush: true, failOnError: true)

      log.debug("Done")
      result.result = "OK"
    } else {
      log.error("Could not find either the user object for deletion (${params.id}) or the placeholder user")
      result.result = "ERROR"
      result.errors = user_to_delete?.errors
    }
    return result
  }

  def update(User user, def data, params = [:], User adminUser) {
    def result = [:]
    def errors = []
    log.debug("Updating user ${user.id} ..")
    def immutables = ['id', 'username', 'last_alert_check']
    def adminAttributes = [
      'roleIds',
      'curatoryGroupIds',
      'enabled',
      'accountExpired',
      'accountLocked',
      'passwordExpired',
      'last_alert_check',
    ]

    if (user.superUserStatus && !adminUser.superUserStatus) {
      result.errors = [
        user: [
          message: "user $adminUser.username is not allowed to change $user.username",
          code: null
        ]
      ]

      return result
    }

    if (data.password && data.new_password) {
      if (user == adminUser && passwordEncoder.matches(data.password, user.password)) {
        boolean success = changePass(user, data.new_password)

        if (!success) {
          errors << [
            'new_password': [
              message: "New password is not valid!",
              code: 'validation.passwordLength'
            ]
          ]
        }
      }
      else if (user == adminUser) {
        errors << [
          'password': [
            message: "Old password is not valid!",
            code: 'validation.password.noMatch'
          ]
        ]
      }
    }

    data.remove('password')
    data.remove('new_password')

    if (!adminUser.isAdmin() && user != adminUser) {
      errors << [
        user: [
          message: "user $adminUser.username is not allowed to change properties of user $user.username",
          baddata: user.username,
          code: null
        ]
      ]
    }
    data.each { field, value ->
      if (field in immutables && (user[field] != value)) {
        errors << [
          "${field}": [
            message: "property is immutable!",
            baddata: value,
            code: null
          ]
        ]
      }
      if (field in adminAttributes && !adminUser.isAdmin()) {
        errors << [
          user: [message: "user $adminUser.username is not allowed to change property $field of user $user.username",
                          baddata: field,
                          code: null]]
      }
    }

    if (errors.size() > 0) {
      result.errors = errors
      return result
    }
    return modifyUser(user, data, adminUser)
  }

  def create(def data, User adminUser) {
    User user = new User()
    Role roleUser = Role.findByAuthority("ROLE_USER")

    if (!data.roleIds)
      data.roleIds = []

    data.roleIds << roleUser.id

    return modifyUser(user, data, adminUser)
  }

  def activate(userId, User adminUser, boolean alertUser = false) {
    def result = [result: 'OK']
    def errors = [:]
    List default_roles = ['ROLE_USER', 'ROLE_CONTRIBUTOR', 'ROLE_EDITOR']
    User user = User.get(userId)

    if (user) {
      if (!user.enabled || user.accountLocked) {
        user.enabled = true
        user.accountLocked = false
        user.save(flush: true, failOnError: true)
      }
      else {
        log.debug("User was already enabled ..")
      }

      updateRoles(user, default_roles, errors, false, adminUser)

      if (alertUser && user.email) {
        EmailValidator validator = EmailValidator.getInstance()
        def edit_link = grailsApplication.config.getProperty('gokb.uiUrl') ?: grailsApplication.config.getProperty('grails.serverURL')
        def support_address = grailsApplication.config.getProperty('gokb.support.emailTo')
        def alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')
        Locale locale = new Locale(user.preferredLocaleString ?: grailsApplication.config.getProperty('gokb.support.locale', String, 'en'))

        if (edit_link && support_address && alerts_address && validator.isValid(user.email)) {
          def content = renderEmail(
            ACTIVATION_NOTICE_TEMPLATE, EMAIL_LAYOUT,
            [
              supportAddress: support_address,
              username: user.username,
              url   : edit_link,
              locale: locale
            ]
          )

          mailService.sendMail {
            to user.email
            from alerts_address
            subject messageSource.getMessage('user.activation.email.subject', null, locale)
            html content
          }
        }
        else if (!edit_link || !support_address || !alerts_address) {
          log.warn("activate:: Missing config value! (gokb.support.emailTo: ${support_address ? 'SET' : 'MISSING'}, gokb.alerts.emailFrom: ${alerts_address ? 'SET' : 'MISSING'}, gokb.uiUrl/grails.serverURL: ${edit_link ? 'SET' : 'MISSING'})")

          result.result = 'WARNING'
          result.message = 'Failed to send activation alert due to missing config value'
          result.warnings = [
            [
              message: 'Unable to send activation info mail due to missing config value for support communication!',
              messageCode: 'component.user.error.activate.missingConfig',
              data: [
                emailFrom: alerts_address ? 'OK' : 'MISSING',
                support: support_address ? 'OK' : 'MISSING',
                url: edit_link ? 'OK' : 'MISSING'
              ]
            ]
          ]
        }
        else {
          result.result = 'WARNING'
          result.message = 'Failed to validate user email address!'
          errors.email = [
            [
              message: 'Unable to send user notification due to invalid email address!',
              messageCode: 'component.user.error.activate.invalidMail'
            ]
          ]
        }
      }
      else if (!user.email) {
        result.result = 'WARNING'
        result.message = "User had no attached email address and could therefore not be alerted!"
        result.warnings = [
          [
            message: 'Unable to send activation notification mail due to missing user email!',
            messageCode: 'component.user.error.activate.missingMail'
          ]
        ]
      }
    }
    else {
      result.result = 'ERROR'
      result.code = 404
    }

    if (errors) {
      result.errors = errors
    }

    result
  }

  private boolean changePass(User user, String newpass) {
    boolean result = true

    int minLength = grailsApplication.config.getProperty('grails.plugin.springsecurity.ui.password.minLength', Integer)
    int maxLength = grailsApplication.config.getProperty('grails.plugin.springsecurity.ui.password.maxLength', Integer)

    if (newpass.length() >= minLength && newpass.length() <= maxLength) {
      user.password = newpass
      user.save(flush: true)
    }
    else {
      result = false
    }

    result
  }

  def modifyUser(User user, Map data, User adminUser) {
    boolean isNewUser = user.username == null
    def result = [:]
    def errors = [:]
    // apply changes
    data.each { field, value ->
      if (field != "roleIds" && field != "curatoryGroupIds" && !user.hasProperty(field)) {
        log.error("property user.$field is unknown!")
        errors << [
          "${field}": [
            message: "unknown property",
            baddata: field,
            code: 400
          ]
        ]
      } else {
        if (field == "roleIds") {
          if (!isNewUser) {
            updateRoles(user, value, errors, isNewUser, adminUser)
          }
        } else if (field == "curatoryGroupIds") {
          updateCuratoryGroups(user, value, errors, isNewUser)
        } else if (field == 'defaultPageSize' && value && value instanceof Integer) {
          user[field] = Long.valueOf(value)
        } else {
          user[field] = value
        }
      }
    }

    if (errors.size() == 0) {
      if (user.validate()) {
        user = user.merge(flush: true, failOnError: true)

        if (isNewUser) {
          updateRoles(user, data.roleIds, errors, isNewUser, adminUser)
        }

        result.data = collectUserProps(user)
      } else {
        user.refresh()
        result.errors = user.errors.allErrors
      }
    } else {
      user.refresh()
      result.errors = errors
    }
    return result
  }

  private void updateRoles(User user, roles_list, errors, boolean isNewUser = true, User adminUser = null) {
    Set<Role> newRoles = []
    Set<Role> previousRoles = []

    roles_list.each { roleId ->
      Role newRole

      if (roleId instanceof String) {
        newRole = Role.findByAuthority(roleId)
      }
      else {
        newRole = Role.findById(roleId)
      }

      if (newRole) {
        if (!adminUser.superUserStatus && newRole.authority == 'ROLE_SUPERUSER') {
          errors << [
            role: [
              message: "User is not allowed to add this role.",
              baddata: roleId,
              code: 403
            ]
          ]
        }
        else {
          newRoles.add(newRole)
        }
      } else {
        log.error("Role ID $roleId not found!")
        errors << [
          role: [
            message: "role ID is unknown",
            baddata: roleId,
            code: 404
          ]
        ]
      }
    }

    if (!isNewUser) {
      UserRole.findAllByUser(user).each { ur ->
        previousRoles << ur.role
      }
    }

    Role.findAll().each { role ->
      if (newRoles.contains(role)) {
        if (!previousRoles.contains(role)) {
          UserRole.create(user, role, true)
        }
      } else if (!isNewUser && previousRoles.contains(role)) {
        UserRole.remove(user, role, true)
      }
    }
  }

  private void updateCuratoryGroups(user, groups_list, errors, boolean isNewUser = true) {
    // change cGroups
    Set<CuratoryGroup> curGroups = []

    groups_list.each { cgId ->
      CuratoryGroup cg_obj = null

      if (cgId) {
        cg_obj = CuratoryGroup.findByUuid(cgId)
      }

      if (!cg_obj) {
        cg_obj = CuratoryGroup.findById(cgId)
      }

      if (cg_obj) {
        curGroups.add(cg_obj)
      } else {
        log.error("CuratoryGroup ID ${cgId} not found!")
        errors << [
          curatoryGroup: [
            message: "unknown CuratoryGroup ID",
            baddata: cgId,
            code: 404
          ]
        ]
      }
    }

    if (isNewUser) {
      user.curatoryGroups = curGroups
    } else {
      user.curatoryGroups.addAll(curGroups)
      user.curatoryGroups.retainAll(curGroups)
    }
  }

  def collectUserProps(User user, params = [:]) {
    def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    def includes = []
    def excludes = []
    def newUserData = [
      id             : user.id,
      username       : user.username,
      displayName    : user.displayName,
      email          : user.email,
      preferredLocaleString: user.preferredLocaleString,
      enabled        : user.enabled,
      accountExpired : user.accountExpired,
      accountLocked  : user.accountLocked,
      passwordExpired: user.passwordExpired,
      status         : user.enabled && !user.accountExpired && !user.accountLocked && !user.passwordExpired,
      defaultPageSize: user.defaultPageSize
    ]

    if (params._include)
      includes = params._include.split(',')
    if (params._exclude) {
      excludes = params._exclude.split(',')
      includes.each { prop ->
        excludes -= prop
      }
    }

    newUserData = newUserData.findAll { k, v ->
      (!excludes.contains(k) || (!includes.empty && includes.contains(k)))
    }

    if (!params._embed || params._embed.split(',').contains("curatoryGroups")) {
      newUserData.curatoryGroups = []
      user.curatoryGroups.each { group ->
        newUserData.curatoryGroups += [
          id    : group.id,
          name  : group.name,
          _links: [
            'self': [href: base + "/curatoryGroups/$group.id"]
          ]
        ]
      }
    }

    if (!params._embed || params._embed.split(',').contains("roles")) {
      newUserData.roles = []
      user.authorities.each { role ->
        newUserData.roles += [
          id       : role.id,
          authority: role.authority,
          _links   : [
            'self': [href: base + "/roles/$role.id"]
          ]
        ]
      }
    }

    newUserData._links = [
      self  : [href: base + "/users/$user.id"],
      update: [href: base + "/users/$user.id"],
      delete: [href: base + "/users/$user.id"]
    ]

    return newUserData
  }

	private String renderEmail(String viewPath, String layoutPath, Map model) {
		String content = groovyPageRenderer.render(view: viewPath, model: model)
		return groovyPageRenderer.render(view: layoutPath, model: model << [content: content])
	}
}
