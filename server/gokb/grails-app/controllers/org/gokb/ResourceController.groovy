package org.gokb

class ResourceController {

  def genericOIDService

  def index() { 
  }

  def show() {
    def result = [:]

    if ( params.id ) {
      log.debug("Attempt to retrieve ${params.id} and find a global template for it");
      result.displayobj = genericOIDService.resolveOID(params.id)
      if ( result.displayobj ) {
        result.displayobjclassname = result.displayobj.class.name
        result.displaytemplate = globalDisplayTemplates[result.displayobjclassname]
        log.debug("resutl of lookup: ${result}");
      }
      else {
        log.debug("unable to resolve object");
      }
    }

    result
  }

  // Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
  def globalDisplayTemplates = [
    'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
    'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
    'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
    'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
    'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
    'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
    'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ]
  ]

}
