package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.Role
import org.gokb.cred.User
import org.springframework.security.access.annotation.Secured

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class ProfileController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def sessionFactory
  def messageService

  def show() {
    def result = [:]
    def user = User.get(springSecurityService.principal.id)

    def cur_groups = []

    user.curatoryGroups?.each { cg ->
      cur_groups.add([name: cg.name, id: cg.id, uuid: cg.uuid])
    }

    def roles = []
    Role.findAll().each { role ->
      if (user.hasRole(role.authority))
        roles.add(role)
    }

    result = [
        'id'             : user.id,
        'username'       : user.username,
        'displayName'    : user.displayName,
        'email'          : user.email,
        'curatoryGroups' : cur_groups,
        'enabled'        : user.enabled,
        'accountExpired' : user.accountExpired,
        'accountLocked'  : user.accountLocked,
        'passwordExpired': user.accountExpired,
        'defaultPageSize': user.defaultPageSize,
        'roles'          : roles
    ]

    render result as JSON
  }

  @Transactional
  def update() {
    def result = ['result': 'OK']
    def immutables = ['id', 'username', 'enabled', 'accountExpired', 'accountLocked', 'passwordExpired', 'last_alert_check']
    def errors = []
    def skippedCG = false
    def reqBody = request.JSON
    User user = springSecurityService.currentUser


    if (reqBody && reqBody.id && reqBody.id == user.id) {
      reqBody.each { field, val ->
        if (val && user.hasProperty(field)) {
          if (field != 'curatoryGroups') {
            if (!immutables.contains(field)) {
              user."${field}" = val
            } else {
              log.debug("Ignoring immutable field ${field}")
            }
          } else {
            if (user.hasRole('ROLE_EDITOR') || user.hasRole('ROLE_ADMIN')) {
              def curGroups = []
              val.each { cg ->
                def cg_obj = null

                if (cg.uuid?.trim()) {
                  cg_obj = CuratoryGroup.findByUuid(cg.uuid)
                }

                if (!cg_obj && cg.id) {
                  cg_obj = cg.id instanceof String ? genericOIDService.resolveOID(cg.id) : CuratoryGroup.get(cg.id)
                }

                if (cg_obj) {
                  curGroups.add(cg_obj)
                } else {
                  log.debug("CuratoryGroup ${cg} not found!")
                  errors << ['message': 'Could not find referenced curatory group!', 'baddata': cg]
                }
              }
              log.debug("New CuratoryGroups: ${curGroups}")

              if (errors.size() > 0) {
                result.message = "There have been errors updating the users curatory groups."
                result.errors = errors
                response.setStatus(400)
              } else {
                user.curatoryGroups.addAll(curGroups)
                user.curatoryGroups.retainAll(curGroups)
              }
            } else {
              skippedCG = true
            }
          }
        }
      }

      if (errors.size() == 0) {
        if (user.validate()) {
          user.save(flush: true)
          result.message = "User profile sucessfully updated."
        } else {
          result.result = "ERROR"
          result.message = "There have been errors saving the user object."
          result.errors = user.errors
        }
      }
    } else {
      log.debug("Missing update payload or wrong user id")
      result.result = "ERROR"
      response.setStatus(400)
      result.message = "Missing update payload or wrong user id!"
    }
    render result as JSON
  }
}
