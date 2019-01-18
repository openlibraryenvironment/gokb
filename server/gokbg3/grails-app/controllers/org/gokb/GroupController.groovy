package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

import org.gokb.cred.*


class GroupController {

  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    def result = [:]
    if ( params.id ) {
      User user = springSecurityService.currentUser

      log.debug("Entering GroupController:index ${params}");

      result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 20 );
      result.pkg_offset = params.pkg_offset ? Integer.parseInt(params.pkg_offset) : 0;
      result.rr_offset = params.rr_offset ? Integer.parseInt(params.rr_offset) : 0;

      if( params.pkg_jumpToPage ){
        result.pkg_offset = ( ( Integer.parseInt(params.pkg_jumpToPage) - 1 ) * result.max )
      }
      params.pkg_offset = result.pkg_offset
      params.remove('pkg_jumpToPage')

      if( params.rr_jumpToPage ){
        result.rr_offset = ( ( Integer.parseInt(params.rr_jumpToPage) - 1 ) * result.max )
      }
      params.rr_offset = result.rr_offset
      params.remove('rr_jumpToPage')

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
      def pkg_curgroup_rdv = RefdataCategory.lookup('Combo.Type', 'Package.CuratoryGroups')

      def cg_packages_hql = " from Package as p where exists ( select c from p.outgoingCombos as c where c.toComponent = :cg and c.type = :ct)"

      result.package_count = Package.executeQuery('select count(p) '+cg_packages_hql,[cg: result.group, ct: pkg_curgroup_rdv])[0];
      result.packages = Package.executeQuery('select p '+cg_packages_hql + " order by ${pkg_sort} ${pkg_sort_order}",[cg: result.group, ct: pkg_curgroup_rdv],
        [max:result.max,offset:result.pkg_offset]);

      result.pkg_page_max = (result.package_count / result.max).toInteger() + (result.package_count % result.max > 0 ? 1 : 0)

      result.pkg_page = (result.pkg_offset / result.max) + 1

      result.withoutJump = params.clone()
      result.remove('pkg_jumpToPage');
      result.remove('rr_jumpToPage');
      result.withoutJump.remove('pkg_jumpToPage');
      result.withoutJump.remove('rr_jumpToPage');
    }
    return result
  }
}
