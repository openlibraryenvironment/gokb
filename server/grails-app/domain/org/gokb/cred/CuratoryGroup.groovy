package org.gokb.cred

class CuratoryGroup extends KBComponent {

  static belongsTo = User
  static final String RDC_ORGA_TYPE = "CuratoryGroup.OrganizationType"

  User owner
  String email
  CuratoryGroupType type
  CuratoryGroup superordinatedGroup
  RefdataValue organizationType

  static hasMany = [
    users: User,
    subordinatedGroups: CuratoryGroup
  ]

  static mapping = {
    includes KBComponent.mapping
    organizationType column:'cg_orgatype_fk_rv'
  }

  static mappedBy = [users: "curatoryGroups", subordinatedGroups: "superordinatedGroup"]

  static manyByCombo = [
    licenses: License,
    packages: Package,
    platforms: Platform,
    orgs: Org,
    offices: Office,
    sources: Source
  ]

  static mappedByCombo = [
    licenses: 'curatoryGroups',
    packages: 'curatoryGroups',
    platforms: 'curatoryGroups',
    orgs: 'curatoryGroups',
    offices: 'curatoryGroups',
    sources: 'curatoryGroups'
  ]

  static constraints = {
    owner (nullable:true, blank:false)
    name (validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = CuratoryGroup.findAllByNameIlikeAndStatusNotEqual(val, status_deleted);

          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        } else {
          return ['notNull']
        }
      }
    })
    email (nullable: true, blank: false, email: true)
    superordinatedGroup (nullable:true, blank:false)
    subordinatedGroups (empty:true)
    organizationType (nullable: true, blank: false)
  }


  private static refdataDefaults = [
    "organizationType"		: "Library"
  ]


  public String getRestPath() {
    return "/curatoryGroups";
  }

  @Override
  public String getNiceName() {
    return "Curatory Group";
  }

  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def ql = null;
    ql = CuratoryGroup.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted ,params)

    ql.each { t ->
      if( !params.filter1 || t.status?.value == params.filter1 ){
        result.add([id:"${t.class.name}:${t.id}", text:"${t.name}", status:"${t.status?.value}"])
      }
    }

    result
  }

  def beforeInsert() {
    def user = springSecurityService?.currentUser
    this.owner = user

    this.generateShortcode()
    this.generateNormname()
    this.generateComponentHash()
    this.generateUuid()
    this.ensureDefaults()
  }
}

