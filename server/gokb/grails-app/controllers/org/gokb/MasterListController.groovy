package org.gokb

import org.gokb.cred.*
import grails.converters.*
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils


@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
class MasterListController {

  def genericOIDService
  def classExaminationService

  def index() { 
    def result = [:]

    // Generate list of cp orgs where a tipp exists for that org as a cp
    result.orgs = Org.executeQuery("select o from Org as o where exists ( select p from Package as p join p.outgoingCombos as ic where ic.toComponent = o and ic.type.value='Package.Provider')");

    result
  }

  def org() { 
    def result = [:]

    // Generate list of cp orgs where a tipp exists for that org as a cp
    // result.titles = Org.executeQuery("select ti from TitleInstance as ti where exists ( select tipp from TitleInstancePackagePlatform as tipp join tipp.outgoingCombos as oc join tipp.outgoingCombos as pkgcombo join where oc.toComponent = ti and ic.type.value='TitleInstancePackagePlatform.Title' and pkgcombo.type.value='TitleInstancePackagePlatform.Package' )");

    // select ti from TitleInstance as ti
    // where exists (
    //   select tipp 
    //   from TitleInstancePackagePlatform as tipp
    //       join tipp.outgoingCombos as tipp_title_combos
    //       join tipp.outgoingCombos as tipp_pkg_combos
    //       join tipp_pkg_combos.toComponent.outgoingCombos as pkg_provider_combos
    //   where tipp_title_combos.type.value='TitleInstancePackagePlatform.Title'
    //     and tipp_pkg_combos.type.value='TitleInstancePackagePlatform.Package'
    //     and pkg_provider_combos.type.value='Package.Provider'
    //     and pkg_provider_combos.toComponent = ?
    // )

    Org o = Org.get(params.id)

    def c = TitleInstance.createCriteria()

    result.titles = c.list {
      // Title
      incomingCombos {
        type {
          eq('value','TitleInstancePackagePlatform.Title')
        }
        fromComponent {
          // tipp
          outgoingCombos {
            type {
              eq('value','TitleInstancePackagePlatform.Package')
            }
            toComponent {
              // Package
              outgoingCombos {
                type {
                  eq('value','Package.Provider')
                }
                eq('toComponent',o)
              }
            }
          }
        }
      }
    }

    log.debug("masterlist for ${o.name} contains ${result.titles.size()} entries");

    result
  }


}
