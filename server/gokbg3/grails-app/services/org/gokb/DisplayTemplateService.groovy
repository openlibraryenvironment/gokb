package org.gokb;

public class DisplayTemplateService {

  private globalDisplayTemplates = new java.util.HashMap<String,Map>()

  @javax.annotation.PostConstruct
  def init() {
    globalDisplayTemplates.put('org.gokb.cred.AdditionalPropertyDefinition',[ type:'staticgsp', rendername:'addpropdef' ]);
    globalDisplayTemplates.put('org.gokb.cred.Package',[ type:'staticgsp', rendername:'package' ]);
    globalDisplayTemplates.put('org.gokb.cred.Org',[ type:'staticgsp', rendername:'org' ]);
    globalDisplayTemplates.put('org.gokb.cred.Platform',[ type:'staticgsp', rendername:'platform' ]);
    globalDisplayTemplates.put('org.gokb.cred.TitleInstance',[ type:'staticgsp', rendername:'title', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.BookInstance',[ type:'staticgsp', rendername:'book' ]);
    globalDisplayTemplates.put('org.gokb.cred.JournalInstance',[ type:'staticgsp', rendername:'journal' ]);
    globalDisplayTemplates.put('org.gokb.cred.DatabaseInstance',[ type:'staticgsp', rendername:'database' ]);
    globalDisplayTemplates.put('org.gokb.cred.OtherInstance',[ type:'staticgsp', rendername:'othertitle' ]);
    globalDisplayTemplates.put('org.gokb.cred.TitleInstancePackagePlatform',[ type:'staticgsp', rendername:'tipp', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.refine.Rule',[ type:'staticgsp', rendername:'rule', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.refine.RefineProject',[ type:'staticgsp', rendername:'project', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.RefdataCategory',[ type:'staticgsp', rendername:'rdc' ]);
    globalDisplayTemplates.put('org.gokb.cred.RefdataValue',[ type:'staticgsp', rendername:'rdv', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.ReviewRequest',[ type:'staticgsp', rendername:'revreq' ]);
    globalDisplayTemplates.put('org.gokb.cred.Office',[ type:'staticgsp', rendername:'office' ]);
    globalDisplayTemplates.put('org.gokb.cred.CuratoryGroup',[ type:'staticgsp', rendername:'curatory_group' ]);
    globalDisplayTemplates.put('org.gokb.cred.License',[ type:'staticgsp', rendername:'license' ]);
    globalDisplayTemplates.put('org.gokb.cred.User',[ type:'staticgsp', rendername:'user', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.Source',[ type:'staticgsp', rendername:'source' ]);
    globalDisplayTemplates.put('org.gokb.cred.DataFile',[ type:'staticgsp', rendername:'datafile', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.KBDomainInfo',[ type:'staticgsp', rendername:'domainInfo', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.JobResult',[ type:'staticgsp', rendername:'job_result', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.Imprint',[ type:'staticgsp', rendername:'imprint' ]);
    globalDisplayTemplates.put('org.gokb.cred.Identifier',[ type:'staticgsp', rendername:'identifier', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.IdentifierNamespace',[ type:'staticgsp', rendername:'identifier_namespace' ]);
    globalDisplayTemplates.put('org.gokb.cred.Macro',[ type:'staticgsp', rendername:'macro', noCreate:true ]);
    globalDisplayTemplates.put('org.gokb.cred.DSCategory',[ type:'staticgsp', rendername:'ds_category' ]);
    globalDisplayTemplates.put('org.gokb.cred.DSCriterion',[ type:'staticgsp', rendername:'ds_criterion' ]);
    globalDisplayTemplates.put('org.gokb.cred.Subject',[ type:'staticgsp', rendername:'subject' ]);
    globalDisplayTemplates.put('org.gokb.cred.Person',[ type:'staticgsp', rendername:'person' ]);
    globalDisplayTemplates.put('org.gokb.cred.UserOrganisation',[ type:'staticgsp', rendername:'user_org' ]);
    globalDisplayTemplates.put('org.gokb.cred.Folder',[ type:'staticgsp', rendername:'folder' ]);
    globalDisplayTemplates.put('org.gokb.cred.Work',[ type:'staticgsp', rendername:'work' ]);
  }

  public Map getTemplateInfo(String type) {
    return globalDisplayTemplates.get(type);
  }

}
