package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

import org.gokb.cred.*


class GroupController {

  def grailsApplication
  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    def result = [:]
    if ( params.id ) {
      User user = springSecurityService.currentUser

      result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 20 );
      result.pkg_offset = params.pkg_offset ? Integer.parseInt(params.pkg_offset) : 0;
      result.rr_offset = params.rr_offset ? Integer.parseInt(params.rr_offset) : 0;

      result.group = CuratoryGroup.get(params.id);



      def rr_sort= params.rr_sort?:'displayName'
      def rr_sort_order = params.rr_sort_order?:'desc'

      def closedStat = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed')
      def delStat = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Deleted')
   

      def cg_review_tasks_hql = " from ReviewRequest as rr where allocatedTo in ( select u from CuratoryGroup as cg join cg.users as u where cg = ? ) and rr.status!=? and rr.status!=? "
      result.rr_count = Package.executeQuery('select count(rr) '+cg_review_tasks_hql,[result.group,closedStat,delStat])[0];
      result.rrs = Package.executeQuery('select rr '+cg_review_tasks_hql,[result.group,closedStat,delStat],[max:result.max,offset:result.rr_offset,sort:rr_sort,order:rr_sort_order]);


      result.rr_page_max = (result.rr_count / result.max).toInteger() + (result.rr_count % result.max > 0 ? 1 : 0)
      result.rr_page = (result.rr_offset / result.max) + 1


      def pkg_sort= params.pkg_sort?:'name'
      def pkg_sort_order = params.pkg_sort_order?:'asc'


      def cg_packages_hql = " from Package as p where exists ( select c from p.outgoingCombos as c where c.toComponent = ? and c.type.value = 'Package.CuratoryGroups')"

      result.package_count = Package.executeQuery('select count(p) '+cg_packages_hql,[result.group])[0];
      result.packages = Package.executeQuery('select p '+cg_packages_hql + " order by ${pkg_sort} ${pkg_sort_order}",[result.group],
        [max:result.max,offset:result.pkg_offset]);

      result.pkg_page_max = (result.package_count / result.max).toInteger() + (result.package_count % result.max > 0 ? 1 : 0)

      result.pkg_page = (result.pkg_offset / result.max) + 1
    }
    return result
  }
}
