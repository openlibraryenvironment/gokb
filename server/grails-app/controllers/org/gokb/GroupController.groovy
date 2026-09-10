package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

import org.gokb.cred.*


class GroupController {

  def springSecurityService

  def index() {
    Map result = [:]

    if ( params.id ) {
      User user = springSecurityService.currentUser
      CuratoryGroup group = CuratoryGroup.get(params.id)

      if (!group) {
        result.result = 'ERROR'
        result.status = 404
        response.status = 404
        return result
      }

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

      result.group = group

      String rr_sort = params.rr_sort ?: 'dateCreated'
      String rr_sort_order = params.rr_sort_order ?: 'asc'

      RefdataValue closedStat = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed')
      RefdataValue delStat = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Deleted')
      RefdataValue inactiveStat = RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'Inactive')
      List cg_components = Package.executeQuery("select p.id from Package as p where :group member of (p.curatoryGroups)",[group: group])

      log.debug("Got ${cg_components.size()} connected components")

      def cg_review_tasks_hql = ''' from ReviewRequest as rr where ((
        rr.allocatedTo in ( select u from CuratoryGroup as cg join cg.users as u where cg = :group )
        or rr.componentToReview.id in (:cgcomponents)
      ) or exists (select arc from AllocatedReviewGroup as arc where arc.review = rr and arc.group = :group and
                                                                     arc.status != :inactive))
      and rr.status != :closed and rr.status != :deleted
      '''

      result.rr_count = Package.executeQuery('select count(rr) ' + cg_review_tasks_hql,
                                              [
                                                group:result.group,
                                                cgcomponents:cg_components,
                                                closed:closedStat,
                                                deleted:delStat,
                                                inactive:inactiveStat
                                              ])[0]
      result.rrs = Package.executeQuery('select rr ' + cg_review_tasks_hql + " order by ${rr_sort} ${rr_sort_order}",
                                        [
                                          group:result.group,
                                          cgcomponents:cg_components,
                                          closed:closedStat,
                                          deleted:delStat,
                                          inactive:inactiveStat],
                                          [max:result.max,offset:result.rr_offset])

      result.rr_page_max = (result.rr_count / result.max).toInteger() + (result.rr_count % result.max > 0 ? 1 : 0)
      result.rr_page = (result.rr_offset / result.max) + 1

      String pkg_sort = params.pkg_sort ?: 'name'
      String pkg_sort_order = params.pkg_sort_order ?: 'asc'

      String cg_packages_hql = " from Package as p where :cg member of (p.curatoryGroups)"

      result.package_count = Package.executeQuery('select count(p) '+cg_packages_hql,[cg: result.group])[0];
      result.packages = Package.executeQuery('select p ' + cg_packages_hql + " order by ${pkg_sort} ${pkg_sort_order}",
                                              [cg: result.group],
                                              [max: result.max, offset: result.pkg_offset]);

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
