package gokbg3

import grails.util.Environment
import grails.config.ConfigMap
import grails.core.GrailsClass
import grails.core.GrailsApplication
import grails.converters.JSON
import groovy.json.JsonOutput
import org.apache.commons.collections.CollectionUtils
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.indices.PutMappingRequest
import org.opensearch.common.xcontent.XContentType
import org.gokb.AugmentEzbJob
import org.gokb.AugmentZdbJob
import org.gokb.AutoUpdatePackagesJob
import org.gokb.TippMatchingJob
import org.gokb.LanguagesService

import javax.servlet.http.HttpServletRequest

import grails.plugin.springsecurity.acl.*

import org.gokb.DomainClassExtender
import org.gokb.ComponentStatisticService
import org.gokb.cred.*

import com.k_int.apis.A_Api;
import com.k_int.ConcurrencyManagerService.Job

class BootStrap {

    GrailsApplication grailsApplication
    def aclUtilService
    def gokbAclService
    def cleanupService
    def ComponentStatisticService
    def concurrencyManagerService
    def ESWrapperService

    def init = { servletContext ->

        log.debug("\n\nInit\n\n")

        log.info("\n\n\n **WARNING** \n\n\n - Automatic create of component identifiers index is no longer part of the domain model");
        log.info("Create manually with create index norm_id_value_idx on kbcomponent(kbc_normname(64),id_namespace_fk,class)");

        ContentItem.withTransaction() {
            def appname = ContentItem.findByKeyAndLocale('gokb.appname', 'default') ?: new ContentItem(key: 'gokb.appname', locale: 'default', content: 'GOKb').save(flush: true, failOnError: true)
        }

        KBComponent.withTransaction() {
            cleanUpMissingDomains()
        }

        // Add our custom metaclass methods for all KBComponents.
        alterDefaultMetaclass()

        // Add Custom APIs.
        addCustomApis()

        // Add a custom check to see if this is an ajax request.
        HttpServletRequest.metaClass.isAjax = {
            'XMLHttpRequest' == delegate.getHeader('X-Requested-With')
        }

        // Global System Roles
        KBComponent.withTransaction() {
            def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR') ?: new Role(authority: 'ROLE_CONTRIBUTOR', roleType: 'global').save(failOnError: true)
            def userRole = Role.findByAuthority('ROLE_USER') ?: new Role(authority: 'ROLE_USER', roleType: 'global').save(failOnError: true)
            def editorRole = Role.findByAuthority('ROLE_EDITOR') ?: new Role(authority: 'ROLE_EDITOR', roleType: 'global').save(failOnError: true)
            def adminRole = Role.findByAuthority('ROLE_ADMIN') ?: new Role(authority: 'ROLE_ADMIN', roleType: 'global').save(failOnError: true)
            def apiRole = Role.findByAuthority('ROLE_API') ?: new Role(authority: 'ROLE_API', roleType: 'global').save(failOnError: true)
            def suRole = Role.findByAuthority('ROLE_SUPERUSER') ?: new Role(authority: 'ROLE_SUPERUSER', roleType: 'global').save(failOnError: true)

            log.debug("Create admin user...");
            def adminUser = User.findByUsername('admin')
            if (!adminUser) {
                log.error("No admin user found, create")
                adminUser = new User(
                    username: 'admin',
                    password: 'admin',
                    display: 'Admin',
                    email: 'admin@localhost',
                    enabled: true).save(failOnError: true)
            }

            def ingestAgent = User.findByUsername('ingestAgent')
            if (!ingestAgent) {
                log.error("No ingestAgent user found, create")
                ingestAgent = new User(
                    username: 'ingestAgent',
                    password: 'ingestAgent',
                    display: 'Ingest Agent',
                    email: '',
                    enabled: false).save(failOnError: true)
            }
            def deletedUser = User.findByUsername('deleted')
            if (!deletedUser) {
                log.error("No deleted user found, create")
                deletedUser = new User(
                    username: 'deleted',
                    password: 'deleted',
                    display: 'Deleted User',
                    email: '',
                    enabled: false).save(failOnError: true)
            }

            if (Environment.current != Environment.PRODUCTION) {
                def tempUser = User.findByUsername('tempUser')
                if (!tempUser) {
                    log.error("No tempUser found, create")
                    tempUser = new User(
                        username: 'tempUser',
                        password: 'tempUser',
                        display: 'Temp User',
                        email: '',
                        enabled: true).save(failOnError: true)
                }

                if (!tempUser.authorities.contains(userRole)) {
                    UserRole.create tempUser, userRole
                }
            }

            // Make sure admin user has all the system roles.
            [contributorRole, userRole, editorRole, adminRole, apiRole, suRole].each { role ->
                log.debug("Ensure admin user has ${role} role")
                if (!adminUser.authorities.contains(role)) {
                    UserRole.create adminUser, role
                }
            }
        }

        if (grailsApplication.config.getProperty('gokb.decisionSupport', Boolean, false)) {
            log.debug("Configuring default decision support parameters");
            DSConfig()
        }

        refdataCats()

        registerDomainClasses()

        migrateDiskFilesToDatabase()

        ensureCuratoryGroup(grailsApplication.config.getProperty('gokb.defaultCuratoryGroup'))
        ensureCuratoryGroup(grailsApplication.config.getProperty('gokb.centralGroups.JournalInstance'))

        KBComponent.withTransaction {
            log.info("GoKB missing normalised component names")
            def ctr = 0;
            KBComponent.executeQuery("select kbc.id from KBComponent as kbc where kbc.normname is null and kbc.name is not null").each { kbc_id ->
                def kbc = KBComponent.get(kbc_id)
                log.debug("Repair component with no normalised name.. ${kbc.class.name} ${kbc.id} ${kbc.name}")
                kbc.generateNormname()
                kbc.save(flush: true, failOnError: true)
                ctr++
            }
            log.debug("${ctr} components updated")

            log.info("GoKB remove usused refdata")
            def rr_std = RefdataCategory.lookup('ReviewRequest.StdDesc', 'RR Standard Desc 1')

            if (rr_std) {
                rr_std.delete()
            }

            log.info("GoKB missing normalised identifiers")

            def id_ctr = 0;
            Identifier.executeQuery("select id.id from Identifier as id where id.normname is null and id.value is not null").each { id_id ->
                Identifier i = Identifier.get(id_id)
                i.generateNormname()
                i.save(flush: true, failOnError: true)
                id_ctr++
            }
            log.debug("${id_ctr} identifiers updated")

            log.info("Fix missing Combo status")

            def status_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
            int num_c = Combo.executeUpdate("update Combo set status = :sa where status is null", [sa: status_active])
            log.debug("${num_c} combos updated")

            log.info("GoKB defaultSortKeys()")
            defaultSortKeys()

            log.info("GoKB sourceObjects()")
            sourceObjects()

            log.info("Ensure default Identifier namespaces")
            def targetTypeTitle = RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Title')
            def targetTypeBook = RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Book')
            def targetTypeJournal = RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Journal')
            def targetTypeOrg = RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Org')
            def targetTypePackage = RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Package')
            def namespaces = [
                [
                    value: 'isbn',
                    name: 'ISBN',
                    family: 'isxn',
                    targetType: targetTypeBook,
                    pattern: "^(?=[0-9]{13}\$|(?=(?:[0-9]+-){4})[0-9-]{17}\$)97[89]-?[0-9]{1,5}-?[0-9]+-?[0-9]+-?[0-9]\$"
                ],
                [
                    value: 'pisbn',
                    name: 'Print-ISBN',
                    family: 'isxn',
                    targetType: targetTypeBook,
                    pattern: "^(?=[0-9]{13}\$|(?=(?:[0-9]+-){4})[0-9-]{17}\$)97[89]-?[0-9]{1,5}-?[0-9]+-?[0-9]+-?[0-9]\$"
                ],
                [
                    value: 'issn',
                    name: 'p-ISSN',
                    family: 'isxn',
                    targetType: targetTypeJournal,
                    pattern: "^\\d{4}\\-\\d{3}[\\dX]\$",
                    baseUrl: "https://portal.issn.org/resource/ISSN/"
                ],
                [
                    value: 'eissn',
                    name: 'e-ISSN',
                    family: 'isxn',
                    targetType: targetTypeJournal,
                    pattern: "^\\d{4}\\-\\d{3}[\\dX]\$",
                    baseUrl: "https://portal.issn.org/resource/ISSN/"
                ],
                [
                    value: 'issnl',
                    name: 'ISSN-L',
                    family: 'isxn',
                    targetType: targetTypeJournal,
                    pattern: "^\\d{4}\\-\\d{3}[\\dX]\$",
                    baseUrl: "https://portal.issn.org/resource/ISSN/"
                ],
                [
                    value: 'doi',
                    name: 'DOI',
                    targetType: targetTypeTitle,
                    baseUrl: "https://doi.org/"
                ],
                [
                    value: 'zdb',
                    name: 'ZDB-ID',
                    pattern: "^\\d{7,10}-[\\dxX]\$",
                    targetType: targetTypeJournal,
                    baseUrl: "https://ld.zdb-services.de/resource/"
                ],
                [
                    value: 'isil',
                    name: 'ISIL',
                    targetType: targetTypePackage,
                    pattern: "^(?=[0-9A-Z-]{4,16}\$)[A-Z]{1,4}-[A-Z0-9]{1,11}(-[A-Z0-9]+)?\$",
                    baseUrl: "https://sigel.staatsbibliothek-berlin.de/suche?isil="
                ],
                [
                    value: 'gnd-id',
                    name: 'GND',
                    targetType: targetTypeOrg,
                    pattern: "^\\d{1,10}-[0-9Xx]\$",
                    baseUrl: "https://d-nb.info/gnd/"
                ],
                [
                    value: 'dbpedia',
                    name: 'DBPedia',
                    targetType: targetTypeOrg,
                    baseUrl: "http://dbpedia.org/resource/"
                ],
                [
                    value: 'loc',
                    name: 'LOC',
                    targetType: targetTypeOrg,
                    pattern: "^n[bors]?\\d{8,10}\$",
                    baseUrl: "http://id.loc.gov/authorities/names/"
                ],
                [
                    value: 'isni',
                    name: 'ISNI',
                    targetType: targetTypeOrg,
                    pattern: "^\\d{15}[0-9Xx]\$",
                    baseUrl: "http://isni-url.oclc.nl/isni/"
                ],
                [
                    value: 'viaf',
                    name: 'VIAF',
                    targetType: targetTypeOrg,
                    pattern: "^\\d{1,22}\$",
                    baseUrl: "http://viaf.org/viaf/"
                ],
                [
                    value: 'ncsu',
                    name: 'NCSU',
                    targetType: targetTypeOrg,
                    pattern: "^\\d{8}\$",
                    baseUrl: "https://www.lib.ncsu.edu/ld/onld/"
                ],
                [
                    value: 'wikidata',
                    name: 'WikiData',
                    targetType: targetTypeOrg,
                    pattern: "^(Q|Property:P|Lexeme:L)\\d{1,10}\$",
                    baseUrl: "https://www.wikidata.org/wiki/"
                ],
                [
                    value: 'ezb',
                    name: 'EZB-ID',
                    pattern: "^\\d+\$",
                    baseUrl: "https://ezb.uni-regensburg.de/detail.phtml?jour_id="
                ]
            ]

            if (grailsApplication.config.getProperty('gokb.ezbOpenCollections.url')) {
                namespaces << [
                    value: 'ezb-collection-id',
                    name: 'EZB Collection ID',
                    pattern: "^EZB-[A-Z0-9]{3,5}-\\d{5}\$"
                ]
            }

            namespaces.each { ns ->
                def ns_obj = IdentifierNamespace.findByValue(ns.value)

                if (ns_obj) {
                    if (ns.pattern && !ns_obj.pattern) {
                        ns_obj.pattern = ns.pattern
                    }

                    if (ns.name && !ns_obj.name) {
                        ns_obj.name = ns.name
                    }

                    if (ns.baseUrl && !ns_obj.baseUrl) {
                        ns_obj.baseUrl = ns.baseUrl
                    }

                    ns_obj.save(flush: true)
                } else {
                    ns_obj = new IdentifierNamespace(ns).save(flush: true, failOnError: true)
                }

                    log.info("Ensured ${ns_obj}!")
            }

            log.debug("Register users and override default admin password")
            registerUsers()

            if (grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false)) {
                log.debug("Ensuring Package cache dates")
                registerPkgCache()
            }

            log.debug("Ensuring ElasticSearch index")
            ensureEsIndices()

            Job hk_job = concurrencyManagerService.createJob {
                cleanupService.housekeeping()
            }.startOrQueue()

            hk_job.description = "Bootstrap Identifier Cleanup"
            hk_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'BootstrapIdentifierCleanup')

            hk_job.startTime = new Date()

            log.debug("Checking for missing component statistics")
            ComponentStatisticService.updateCompStats()

            if (Environment.current != Environment.TEST) {
                AugmentZdbJob.schedule(grailsApplication.config.getProperty('gokb.zdbAugment.cron'))
                AugmentEzbJob.schedule(grailsApplication.config.getProperty('gokb.ezbAugment.cron'))
                AutoUpdatePackagesJob.schedule(grailsApplication.config.getProperty('gokb.packageUpdate.cron'))
            }
        }

        log.info("GoKB Init complete")
    }

    private Object ensureCuratoryGroup(String groupName){
        if (groupName != null){
            log.debug("Ensure curatory group: ${groupName}");
            def local_cg = CuratoryGroup.findByName(groupName) ?:
                new CuratoryGroup(name: groupName).save(flush: true, failOnError: true);
        }
    }

    def defaultBulkLoaderConfig() {
        // BulkLoaderConfig
        grailsApplication.config.getProperty('kbart2.mappings', Map, [:]).each { k, v ->
            log.debug("Process ${k}");
            def existing_cfg = BulkLoaderConfig.findByCode(k)
            if (existing_cfg) {
                log.debug("Got existing config");
            } else {
                def cfg = v as JSON
                existing_cfg = new BulkLoaderConfig(code: k, cfg: cfg?.toString()).save(flush: true, failOnError: true)
            }
        }
    }

    def migrateDiskFilesToDatabase() {
        log.info("Migrate Disk Files");
        def baseUploadDir = grailsApplication.config.getProperty('baseUploadDir') ?: '.'

        DataFile.findAll("from DataFile as df where df.fileData is null").each { df ->
            log.debug("Migrating files for ${df.uploadName}::${df.guid}")
            def sub1 = df.guid.substring(0, 2);
            def sub2 = df.guid.substring(2, 4);
            def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${df.guid}";
            try {
                def source_file = new File(temp_file_name);
                df.fileData = source_file.getBytes()
                if (df.save(flush: true)) {
                    //success
                    source_file.delete()
                } else {
                    log.debug("Errors while trying to save DataFile fileData:")
                    log.debug(df.errors)
                }
            } catch (Exception e) {
                log.error("Exception while migrating files to database. File ${temp_file_name}", e)
            }
        }
    }

    def cleanUpMissingDomains() {

        def domains = KBDomainInfo.createCriteria().list { ilike('dcName', 'org.gokb%') }.each { d ->
            try {

                // Just try reading the class.
                Class c = Class.forName(d.dcName)
                // log.debug ("Looking for ${d.dcName} found class ${c}.")

            } catch (ClassNotFoundException e) {
                d.delete(flush: true)
                log.info("Deleted domain object for ${d.dcName} as the Class could not be found.")
            }
        }
    }


    private void addCustomApis() {

        log.debug("Extend Domain classes.")
        (grailsApplication.getArtefacts("Domain")*.clazz).each { Class<?> c ->

            // SO: Changed this to use the APIs 'applicableFor' method that is used to check whether,
            // to add to the class or not. This defaults to "true". Have overriden on the GrailsDomainHelperApi utils
            // and moved the selective code there. This means that *ALL* domain classes will still receive the methods in the
            // SecurityApi.
            // II: has this caused projects under org.gokb.refine to no longer be visible? Not sure how to fix it.

            // log.debug("Considering ${c}")
            grailsApplication.config.getProperty('apiClasses', List, []).each { String className ->
                // log.debug("Adding methods to ${c.name} from ${className}");
                // Add the api methods.
                A_Api.addMethods(c, Class.forName(className))
            }
        }
    }

    def registerDomainClasses() {

        log.debug("Register Domain Classes")
        RefdataValue.withTransaction {
            AclClass aclClass = AclClass.findByClassName('org.gokb.cred.KBDomainInfo') ?: new AclClass(className: 'org.gokb.cred.KBDomainInfo').save(flush: true)

            AclSid sidAdmin = AclSid.findBySid('ROLE_ADMIN') ?: new AclSid(sid: 'ROLE_ADMIN', principal: false).save(flush: true)
            AclSid sidSuperUser = AclSid.findBySid('ROLE_SUPERUSER') ?: new AclSid(sid: 'ROLE_SUPERUSER', principal: false).save(flush: true)
            AclSid sidUser = AclSid.findBySid('ROLE_USER') ?: new AclSid(sid: 'ROLE_USER', principal: false).save(flush: true)
            AclSid sidContributor = AclSid.findBySid('ROLE_CONTRIBUTOR') ?: new AclSid(sid: 'ROLE_CONTRIBUTOR', principal: false).save(flush: true)
            AclSid sidEditor = AclSid.findBySid('ROLE_EDITOR') ?: new AclSid(sid: 'ROLE_EDITOR', principal: false).save(flush: true)
            AclSid sidApi = AclSid.findBySid('ROLE_API') ?: new AclSid(sid: 'ROLE_API', principal: false).save(flush: true)

            RefdataValue std_domain_type = RefdataCategory.lookupOrCreate('DCType', 'Standard').save(flush: true, failOnError: true)
            grailsApplication.domainClasses.each { dc ->
                // log.debug("Ensure ${dc.name} has entry in KBDomainInfo table");
                KBDomainInfo dcinfo = KBDomainInfo.findByDcName(dc.clazz.name)
                if (dcinfo == null) {
                    dcinfo = new KBDomainInfo(dcName: dc.clazz.name, displayName: dc.name, type: std_domain_type);
                    dcinfo.save(flush: true);
                }

                if (dcinfo.dcName.startsWith('org.gokb.cred') || dcinfo.dcName == 'org.gokb.Annotation') {
                    AclObjectIdentity oid

                    if (!AclObjectIdentity.findByObjectId(dcinfo.id)) {
                        oid = new AclObjectIdentity(objectId: dcinfo.id, aclClass: aclClass, owner: sidAdmin, entriesInheriting: false).save(flush: true)
                    }
                }
            }
        }
    }

    def alterDefaultMetaclass = {
        // Inject helpers to Domain classes.
        grailsApplication.domainClasses.each { GrailsClass domainClass ->
            // Extend the domain class.
            DomainClassExtender.extend(domainClass)
        }
    }

    def assertPublisher(name) {
        def p = Org.findByName(name)
        if (!p) {
            def content_provider_role = RefdataCategory.lookupOrCreate('Org Role', 'Content Provider');
            p = new Org(name: name)
            p.tags.add(content_provider_role);
            p.save(flush: true);
        }
    }

    def defaultSortKeys() {
        def vals = RefdataValue.executeQuery("select o from RefdataValue o where o.sortKey is null or trim(o.sortKey) = ''")

        // Default the sort key to 0.
        vals.each {
            it.sortKey = "0"
            it.save(flush: true, failOnError: true)
        }

        // Now we should also do the same for the Domain objects.
        vals = KBDomainInfo.executeQuery("select o from KBDomainInfo o where o.dcSortOrder is null or trim(o.dcSortOrder) = ''")

        // Default the sort key to 0.
        vals.each {
            it.dcSortOrder = "0"
            it.save(flush: true, failOnError: true)
        }
    }

    def destroy = {
    }

    def refdataCats() {
        RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS,
            [(KBComponent.STATUS_CURRENT)  : '0',
            (KBComponent.STATUS_EXPECTED) : '1',
            (KBComponent.STATUS_RETIRED)  : '2',
            (KBComponent.STATUS_DELETED)  : '3'
            ]
        )

        RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED)
        RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_IN_PROGRESS)
        RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_REJECTED)

        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_FORMAT, "Digitised")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_FORMAT, "Electronic")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_FORMAT, "Print")

        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_DELAYED_OA, "No")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_DELAYED_OA, "Unknown")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_DELAYED_OA, "Yes")

        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_HYBRID_OA, "No")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_HYBRID_OA, "Unknown")
        RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_HYBRID_OA, "Yes")

        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "Yes")
        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "No")

        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Paid")
        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "OA")
        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Unknown")

        ['Database', 'Monograph', 'Other', 'Serial'].each { pubType ->
            RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, pubType)
        }

        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Fulltext")
        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Selected Articles")
        RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Abstracts")

        RefdataCategory.lookupOrCreate("TIPPCoverageStatement.CoverageDepth", "Fulltext")
        RefdataCategory.lookupOrCreate("TIPPCoverageStatement.CoverageDepth", "Selected Articles")
        RefdataCategory.lookupOrCreate("TIPPCoverageStatement.CoverageDepth", "Abstracts")

        RefdataCategory.lookupOrCreate("Package.Scope", "Aggregator")
        RefdataCategory.lookupOrCreate("Package.Scope", "Back File")
        RefdataCategory.lookupOrCreate("Package.Scope", "Front File")
        RefdataCategory.lookupOrCreate("Package.Scope", "Master File")
        RefdataCategory.lookupOrCreate("Package.ListStatus", "Checked")
        RefdataCategory.lookupOrCreate("Package.ListStatus", "In Progress")
        RefdataCategory.lookupOrCreate("Package.Breakable", "No")
        RefdataCategory.lookupOrCreate("Package.Breakable", "Yes")
        RefdataCategory.lookupOrCreate("Package.Breakable", "Unknown")
        RefdataCategory.lookupOrCreate("Package.Consistent", "No")
        RefdataCategory.lookupOrCreate("Package.Consistent", "Yes")
        RefdataCategory.lookupOrCreate("Package.Consistent", "Unknown")
        RefdataCategory.lookupOrCreate("Package.Fixed", "No")
        RefdataCategory.lookupOrCreate("Package.Fixed", "Yes")
        RefdataCategory.lookupOrCreate("Package.Fixed", "Unknown")

        RefdataCategory.lookupOrCreate("Package.PaymentType", "Complimentary")
        RefdataCategory.lookupOrCreate("Package.PaymentType", "Limited Promotion")
        RefdataCategory.lookupOrCreate("Package.PaymentType", "Paid")
        RefdataCategory.lookupOrCreate("Package.PaymentType", "Opt Out Promotion")
        RefdataCategory.lookupOrCreate("Package.PaymentType", "Uncharged")
        RefdataCategory.lookupOrCreate("Package.PaymentType", "Unknown")

        RefdataCategory.lookupOrCreate("Package.LinkType", "Parent")
        RefdataCategory.lookupOrCreate("Package.LinkType", "Previous")

        RefdataCategory.lookupOrCreate("Package.Global", "Consortium")
        RefdataCategory.lookupOrCreate("Package.Global", "Regional")
        RefdataCategory.lookupOrCreate("Package.Global", "Global")
        RefdataCategory.lookupOrCreate("Package.Global", "Local")
        RefdataCategory.lookupOrCreate("Package.Global", "Other")

        RefdataCategory.lookupOrCreate("Package.ContentType", "Mixed")
        RefdataCategory.lookupOrCreate("Package.ContentType", "Journal")
        RefdataCategory.lookupOrCreate("Package.ContentType", "Book")
        RefdataCategory.lookupOrCreate("Package.ContentType", "Database")

        RefdataCategory.lookupOrCreate("Platform.AuthMethod", "IP")
        RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Shibboleth")
        RefdataCategory.lookupOrCreate("Platform.AuthMethod", "User Password")
        RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Unknown")

        RefdataCategory.lookupOrCreate("Platform.Role", "Admin")
        RefdataCategory.lookupOrCreate("Platform.Role", "Host")

        RefdataCategory.lookupOrCreate("Platform.Software", "Atupon")

        RefdataCategory.lookupOrCreate("Platform.Service", "Highwire")

        ["A & I Database", "Audio", "Book", "Database", "Dataset", "Film", "Image", "Journal",
        "Other", "Published Score", "Article", "Software", "Statistics", "Market Data", "Standards",
        "Biography", "Legal Text", "Cartography", "Miscellaneous", "Other"].each { med ->
            RefdataCategory.lookupOrCreate("TitleInstance.Medium", med)
            RefdataCategory.lookupOrCreate(TitleInstancePackagePlatform.RD_MEDIUM, med)
        }

        RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Unknown")
        RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Full OA")
        RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Hybrid OA")
        RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "No OA")

        RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Yes")
        RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "No")
        RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Unknown")

        RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Yes")
        RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "No")
        RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Unknown")

        RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Ceased")
        RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Paused")

        RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer Out")
        RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer In")

        RefdataCategory.lookupOrCreate("Tipp.LinkType", "Comes With")
        RefdataCategory.lookupOrCreate("Tipp.LinkType", "Parent")
        RefdataCategory.lookupOrCreate("Tipp.LinkType", "Previous")

        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Translated")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Absorbed")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "In Series")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Merged")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Renamed")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Split")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Supplement")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Transferred")
        RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Unknown")

        RefdataCategory.lookupOrCreate('Org.Mission', 'Academic')
        RefdataCategory.lookupOrCreate('Org.Mission', 'Commercial')
        RefdataCategory.lookupOrCreate('Org.Mission', 'Community Agency')
        RefdataCategory.lookupOrCreate('Org.Mission', 'Consortium')

        RefdataCategory.lookupOrCreate('UserOrganisation.Mission', 'Academic')
        RefdataCategory.lookupOrCreate('UserOrganisation.Mission', 'Commercial')
        RefdataCategory.lookupOrCreate('UserOrganisation.Mission', 'Community Agency')
        RefdataCategory.lookupOrCreate('UserOrganisation.Mission', 'Consortium')

        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Org')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Package')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Title')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Book')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Journal')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Database')
        RefdataCategory.lookupOrCreate('IdentifierNamespace.TargetType', 'Other')

        RefdataCategory.lookupOrCreate('Org.Role', 'Licensor')
        RefdataCategory.lookupOrCreate('Org.Role', 'Licensee')
        RefdataCategory.lookupOrCreate('Org.Role', 'Broker')
        RefdataCategory.lookupOrCreate('Org.Role', 'Vendor')
        RefdataCategory.lookupOrCreate('Org.Role', 'Content Provider')
        RefdataCategory.lookupOrCreate('Org.Role', 'Platform Provider')
        RefdataCategory.lookupOrCreate('Org.Role', 'Issuing Body')
        RefdataCategory.lookupOrCreate('Org.Role', 'Publisher')
        RefdataCategory.lookupOrCreate('Org.Role', 'Imprint')

        RefdataCategory.lookupOrCreate('Country', 'Afghanistan')
        RefdataCategory.lookupOrCreate('Country', 'Albania')
        RefdataCategory.lookupOrCreate('Country', 'Algeria')
        RefdataCategory.lookupOrCreate('Country', 'American Samoa')
        RefdataCategory.lookupOrCreate('Country', 'Andorra')
        RefdataCategory.lookupOrCreate('Country', 'Angola')
        RefdataCategory.lookupOrCreate('Country', 'Anguilla')
        RefdataCategory.lookupOrCreate('Country', 'Antigua and Barbuda')
        RefdataCategory.lookupOrCreate('Country', 'Argentina')
        RefdataCategory.lookupOrCreate('Country', 'Armenia')
        RefdataCategory.lookupOrCreate('Country', 'Aruba')
        RefdataCategory.lookupOrCreate('Country', 'Australia')
        RefdataCategory.lookupOrCreate('Country', 'Austria')
        RefdataCategory.lookupOrCreate('Country', 'Azerbaijan')
        RefdataCategory.lookupOrCreate('Country', 'Bahamas, The')
        RefdataCategory.lookupOrCreate('Country', 'Bahrain')
        RefdataCategory.lookupOrCreate('Country', 'Bangladesh')
        RefdataCategory.lookupOrCreate('Country', 'Barbados')
        RefdataCategory.lookupOrCreate('Country', 'Belarus')
        RefdataCategory.lookupOrCreate('Country', 'Belgium')
        RefdataCategory.lookupOrCreate('Country', 'Belize')
        RefdataCategory.lookupOrCreate('Country', 'Benin')
        RefdataCategory.lookupOrCreate('Country', 'Bermuda')
        RefdataCategory.lookupOrCreate('Country', 'Bhutan')
        RefdataCategory.lookupOrCreate('Country', 'Bolivia')
        RefdataCategory.lookupOrCreate('Country', 'Bosnia')
        RefdataCategory.lookupOrCreate('Country', 'Botswana')
        RefdataCategory.lookupOrCreate('Country', 'Bougainville')
        RefdataCategory.lookupOrCreate('Country', 'Brazil')
        RefdataCategory.lookupOrCreate('Country', 'British Indian Ocean')
        RefdataCategory.lookupOrCreate('Country', 'British Virgin Islands')
        RefdataCategory.lookupOrCreate('Country', 'Brunei')
        RefdataCategory.lookupOrCreate('Country', 'Bulgaria')
        RefdataCategory.lookupOrCreate('Country', 'Burkina Faso')
        RefdataCategory.lookupOrCreate('Country', 'Burundi')
        RefdataCategory.lookupOrCreate('Country', 'Cambodia')
        RefdataCategory.lookupOrCreate('Country', 'Cameroon')
        RefdataCategory.lookupOrCreate('Country', 'Canada')
        RefdataCategory.lookupOrCreate('Country', 'Cape Verde Islands')
        RefdataCategory.lookupOrCreate('Country', 'Cayman Islands')
        RefdataCategory.lookupOrCreate('Country', 'Central African Republic')
        RefdataCategory.lookupOrCreate('Country', 'Chad')
        RefdataCategory.lookupOrCreate('Country', 'Chile')
        RefdataCategory.lookupOrCreate('Country', 'China, Hong Kong')
        RefdataCategory.lookupOrCreate('Country', 'China, Macau')
        RefdataCategory.lookupOrCreate('Country', 'China, People’s Republic')
        RefdataCategory.lookupOrCreate('Country', 'China, Taiwan')
        RefdataCategory.lookupOrCreate('Country', 'Colombia')
        RefdataCategory.lookupOrCreate('Country', 'Comoros')
        RefdataCategory.lookupOrCreate('Country', 'Congo, Democratic Republic of')
        RefdataCategory.lookupOrCreate('Country', 'Congo, Republic of')
        RefdataCategory.lookupOrCreate('Country', 'Cook Islands')
        RefdataCategory.lookupOrCreate('Country', 'Costa Rica')
        RefdataCategory.lookupOrCreate('Country', 'Cote d’Ivoire')
        RefdataCategory.lookupOrCreate('Country', 'Croatia')
        RefdataCategory.lookupOrCreate('Country', 'Cuba')
        RefdataCategory.lookupOrCreate('Country', 'Cyprus')
        RefdataCategory.lookupOrCreate('Country', 'Czech Republic')
        RefdataCategory.lookupOrCreate('Country', 'Denmark')
        RefdataCategory.lookupOrCreate('Country', 'Djibouti')
        RefdataCategory.lookupOrCreate('Country', 'Dominica')
        RefdataCategory.lookupOrCreate('Country', 'Dominican Republic')
        RefdataCategory.lookupOrCreate('Country', 'Ecuador')
        RefdataCategory.lookupOrCreate('Country', 'Egypt')
        RefdataCategory.lookupOrCreate('Country', 'El Salvador')
        RefdataCategory.lookupOrCreate('Country', 'Equatorial Guinea')
        RefdataCategory.lookupOrCreate('Country', 'Eritrea')
        RefdataCategory.lookupOrCreate('Country', 'Estonia')
        RefdataCategory.lookupOrCreate('Country', 'Ethiopia')
        RefdataCategory.lookupOrCreate('Country', 'Faeroe Islands')
        RefdataCategory.lookupOrCreate('Country', 'Falkland Islands')
        RefdataCategory.lookupOrCreate('Country', 'Federated States of Micronesia')
        RefdataCategory.lookupOrCreate('Country', 'Fiji')
        RefdataCategory.lookupOrCreate('Country', 'Finland')
        RefdataCategory.lookupOrCreate('Country', 'France')
        RefdataCategory.lookupOrCreate('Country', 'French Guiana')
        RefdataCategory.lookupOrCreate('Country', 'French Polynesia')
        RefdataCategory.lookupOrCreate('Country', 'Gabon')
        RefdataCategory.lookupOrCreate('Country', 'Gambia, The')
        RefdataCategory.lookupOrCreate('Country', 'Georgia')
        RefdataCategory.lookupOrCreate('Country', 'Germany')
        RefdataCategory.lookupOrCreate('Country', 'Ghana')
        RefdataCategory.lookupOrCreate('Country', 'Gibraltar')
        RefdataCategory.lookupOrCreate('Country', 'Greece')
        RefdataCategory.lookupOrCreate('Country', 'Greenland')
        RefdataCategory.lookupOrCreate('Country', 'Grenada')
        RefdataCategory.lookupOrCreate('Country', 'Guadeloupe')
        RefdataCategory.lookupOrCreate('Country', 'Guam')
        RefdataCategory.lookupOrCreate('Country', 'Guatemala')
        RefdataCategory.lookupOrCreate('Country', 'Guinea')
        RefdataCategory.lookupOrCreate('Country', 'Guinea-Bissau')
        RefdataCategory.lookupOrCreate('Country', 'Guyana')
        RefdataCategory.lookupOrCreate('Country', 'Haiti')
        RefdataCategory.lookupOrCreate('Country', 'Holy See (Vatican City State)')
        RefdataCategory.lookupOrCreate('Country', 'Honduras')
        RefdataCategory.lookupOrCreate('Country', 'Hungary')
        RefdataCategory.lookupOrCreate('Country', 'Iceland')
        RefdataCategory.lookupOrCreate('Country', 'India')
        RefdataCategory.lookupOrCreate('Country', 'Indonesia')
        RefdataCategory.lookupOrCreate('Country', 'Iran')
        RefdataCategory.lookupOrCreate('Country', 'Iraq')
        RefdataCategory.lookupOrCreate('Country', 'Ireland')
        RefdataCategory.lookupOrCreate('Country', 'Israel')
        RefdataCategory.lookupOrCreate('Country', 'Italy')
        RefdataCategory.lookupOrCreate('Country', 'Jamaica')
        RefdataCategory.lookupOrCreate('Country', 'Japan')
        RefdataCategory.lookupOrCreate('Country', 'Jordan')
        RefdataCategory.lookupOrCreate('Country', 'Kazakhstan')
        RefdataCategory.lookupOrCreate('Country', 'Kenya')
        RefdataCategory.lookupOrCreate('Country', 'Kiribati')
        RefdataCategory.lookupOrCreate('Country', 'Korea, Democratic People’s Rep')
        RefdataCategory.lookupOrCreate('Country', 'Korea, Republic of')
        RefdataCategory.lookupOrCreate('Country', 'Kosovo')
        RefdataCategory.lookupOrCreate('Country', 'Kuwait')
        RefdataCategory.lookupOrCreate('Country', 'Kyrgyzstan')
        RefdataCategory.lookupOrCreate('Country', 'Laos')
        RefdataCategory.lookupOrCreate('Country', 'Latvia')
        RefdataCategory.lookupOrCreate('Country', 'Lebanon')
        RefdataCategory.lookupOrCreate('Country', 'Lesotho')
        RefdataCategory.lookupOrCreate('Country', 'Liberia')
        RefdataCategory.lookupOrCreate('Country', 'Libya')
        RefdataCategory.lookupOrCreate('Country', 'Liechtenstein')
        RefdataCategory.lookupOrCreate('Country', 'Lithuania')
        RefdataCategory.lookupOrCreate('Country', 'Luxembourg')
        RefdataCategory.lookupOrCreate('Country', 'Macedonia')
        RefdataCategory.lookupOrCreate('Country', 'Madagascar')
        RefdataCategory.lookupOrCreate('Country', 'Malawi')
        RefdataCategory.lookupOrCreate('Country', 'Malaysia')
        RefdataCategory.lookupOrCreate('Country', 'Maldives')
        RefdataCategory.lookupOrCreate('Country', 'Mali')
        RefdataCategory.lookupOrCreate('Country', 'Malta')
        RefdataCategory.lookupOrCreate('Country', 'Martinique')
        RefdataCategory.lookupOrCreate('Country', 'Mauritania')
        RefdataCategory.lookupOrCreate('Country', 'Mauritius')
        RefdataCategory.lookupOrCreate('Country', 'Mayotte')
        RefdataCategory.lookupOrCreate('Country', 'Mexico')
        RefdataCategory.lookupOrCreate('Country', 'Moldova')
        RefdataCategory.lookupOrCreate('Country', 'Monaco')
        RefdataCategory.lookupOrCreate('Country', 'Mongolia')
        RefdataCategory.lookupOrCreate('Country', 'Montenegro')
        RefdataCategory.lookupOrCreate('Country', 'Montserrat')
        RefdataCategory.lookupOrCreate('Country', 'Morocco Mozambique')
        RefdataCategory.lookupOrCreate('Country', 'Myanmar')
        RefdataCategory.lookupOrCreate('Country', 'Namibia')
        RefdataCategory.lookupOrCreate('Country', 'Nauru')
        RefdataCategory.lookupOrCreate('Country', 'Nepal')
        RefdataCategory.lookupOrCreate('Country', 'Netherlands')
        RefdataCategory.lookupOrCreate('Country', 'Netherlands Antilles')
        RefdataCategory.lookupOrCreate('Country', 'New Caledonia')
        RefdataCategory.lookupOrCreate('Country', 'New Zealand')
        RefdataCategory.lookupOrCreate('Country', 'Nicaragua')
        RefdataCategory.lookupOrCreate('Country', 'Niger')
        RefdataCategory.lookupOrCreate('Country', 'Nigeria')
        RefdataCategory.lookupOrCreate('Country', 'Norway')
        RefdataCategory.lookupOrCreate('Country', 'Oman')
        RefdataCategory.lookupOrCreate('Country', 'Pakistan')
        RefdataCategory.lookupOrCreate('Country', 'Palestine')
        RefdataCategory.lookupOrCreate('Country', 'Panama')
        RefdataCategory.lookupOrCreate('Country', 'Papua New Guinea')
        RefdataCategory.lookupOrCreate('Country', 'Paraguay')
        RefdataCategory.lookupOrCreate('Country', 'Peru')
        RefdataCategory.lookupOrCreate('Country', 'Philippines')
        RefdataCategory.lookupOrCreate('Country', 'Poland')
        RefdataCategory.lookupOrCreate('Country', 'Portugal')
        RefdataCategory.lookupOrCreate('Country', 'Puerto Rico')
        RefdataCategory.lookupOrCreate('Country', 'Qatar')
        RefdataCategory.lookupOrCreate('Country', 'Réunion')
        RefdataCategory.lookupOrCreate('Country', 'Romania')
        RefdataCategory.lookupOrCreate('Country', 'Russia')
        RefdataCategory.lookupOrCreate('Country', 'Rwanda')
        RefdataCategory.lookupOrCreate('Country', 'Saint Barthelemy')
        RefdataCategory.lookupOrCreate('Country', 'Saint Helena')
        RefdataCategory.lookupOrCreate('Country', 'Saint Kitts & Nevis')
        RefdataCategory.lookupOrCreate('Country', 'Saint Lucia')
        RefdataCategory.lookupOrCreate('Country', 'Saint Martin')
        RefdataCategory.lookupOrCreate('Country', 'Saint Pierre & Miquelon')
        RefdataCategory.lookupOrCreate('Country', 'Saint Vincent')
        RefdataCategory.lookupOrCreate('Country', 'Samoa')
        RefdataCategory.lookupOrCreate('Country', 'San Marino')
        RefdataCategory.lookupOrCreate('Country', 'Sao Tomé & Principe')
        RefdataCategory.lookupOrCreate('Country', 'Saudi Arabia')
        RefdataCategory.lookupOrCreate('Country', 'Senegal')
        RefdataCategory.lookupOrCreate('Country', 'Serbia')
        RefdataCategory.lookupOrCreate('Country', 'Seychelles')
        RefdataCategory.lookupOrCreate('Country', 'Sierra Leone')
        RefdataCategory.lookupOrCreate('Country', 'Singapore')
        RefdataCategory.lookupOrCreate('Country', 'Slovakia')
        RefdataCategory.lookupOrCreate('Country', 'Slovenia')
        RefdataCategory.lookupOrCreate('Country', 'Solomon Islands')
        RefdataCategory.lookupOrCreate('Country', 'Somalia')
        RefdataCategory.lookupOrCreate('Country', 'South Africa')
        RefdataCategory.lookupOrCreate('Country', 'Spain')
        RefdataCategory.lookupOrCreate('Country', 'Sri Lanka')
        RefdataCategory.lookupOrCreate('Country', 'Sudan')
        RefdataCategory.lookupOrCreate('Country', 'Suriname')
        RefdataCategory.lookupOrCreate('Country', 'Swaziland')
        RefdataCategory.lookupOrCreate('Country', 'Sweden')
        RefdataCategory.lookupOrCreate('Country', 'Switzerland')
        RefdataCategory.lookupOrCreate('Country', 'Syria')
        RefdataCategory.lookupOrCreate('Country', 'Tajikistan')
        RefdataCategory.lookupOrCreate('Country', 'Tanzania')
        RefdataCategory.lookupOrCreate('Country', 'Thailand')
        RefdataCategory.lookupOrCreate('Country', 'Timor Leste')
        RefdataCategory.lookupOrCreate('Country', 'Togo')
        RefdataCategory.lookupOrCreate('Country', 'Tokelau Islands')
        RefdataCategory.lookupOrCreate('Country', 'Tonga')
        RefdataCategory.lookupOrCreate('Country', 'Trinidad & Tobago')
        RefdataCategory.lookupOrCreate('Country', 'Tunisia')
        RefdataCategory.lookupOrCreate('Country', 'Turkey')
        RefdataCategory.lookupOrCreate('Country', 'Turkmenistan')
        RefdataCategory.lookupOrCreate('Country', 'Turks & Caicos Islands')
        RefdataCategory.lookupOrCreate('Country', 'Tuvalu')
        RefdataCategory.lookupOrCreate('Country', 'Uganda')
        RefdataCategory.lookupOrCreate('Country', 'Ukraine')
        RefdataCategory.lookupOrCreate('Country', 'United Arab Emirates')
        RefdataCategory.lookupOrCreate('Country', 'United Kingdom of GB & NI')
        RefdataCategory.lookupOrCreate('Country', 'United States of America')
        RefdataCategory.lookupOrCreate('Country', 'Uruguay')
        RefdataCategory.lookupOrCreate('Country', 'US Virgin Islands')
        RefdataCategory.lookupOrCreate('Country', 'Uzbekistan')
        RefdataCategory.lookupOrCreate('Country', 'Vanuatu')
        RefdataCategory.lookupOrCreate('Country', 'Venezuela')
        RefdataCategory.lookupOrCreate('Country', 'Vietnam')
        RefdataCategory.lookupOrCreate('Country', 'Wallis & Futuna Islands')
        RefdataCategory.lookupOrCreate('Country', 'Yemen')
        RefdataCategory.lookupOrCreate('Country', 'Zambia')
        RefdataCategory.lookupOrCreate('Country', 'Zimbabwe')
        //    RefdataCategory.lookupOrCreate("Combo.Type", "Content Provider").save()
        RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_ACTIVE)
        RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_DELETED)
        RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_SUPERSEDED)
        RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_EXPIRED)

        RefdataCategory.lookupOrCreate('License.Type', 'Template')
        RefdataCategory.lookupOrCreate('License.Type', 'Other')

        RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Misspelling')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Authorized')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Acronym')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Minor Change')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Nickname')

        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'en_US')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'en_GB')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'en')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'de')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'es')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'fr')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'it')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'ru')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'pt')
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale', 'la')

        RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT)
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_DELETED)
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_EXPECTED)
        RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_RETIRED)

        // Review Request
        RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open')
        RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed')
        RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Deleted')

        RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'Claimed')
        RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'In Progress')
        RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'Inactive')

        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Minor Identifier Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Major Identifier Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Multiple Matches')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Type Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Name Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Name Similarity')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Namespace Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Platform Noncurrent')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'New Platform')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'New Org')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Status Deleted')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Status Retired')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'TIPPs Retired')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Invalid TIPPs')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Ambiguous Matches')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Multiple ZDB Results')
        RefdataCategory.lookupOrCreate("ReviewRequest.StdDesc", "Coverage Mismatch")
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'No ZDB Results')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'ZDB Title Overlap')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Multiple EZB Results')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'No EZB Results')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'EZB Title Overlap')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Ambiguous Title Matches')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Namespace Conflict')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Critical Identifier Conflict')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Secondary Identifier Conflict')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Generic Matching Conflict')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Invalid Record')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Import Identifier Mismatch')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Ambiguous Record Matches')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Import Report')
        RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Information')
        RefdataCategory.lookupOrCreate("ReviewRequest.StdDesc", "Invalid Name")
        RefdataCategory.lookupOrCreate("ReviewRequest.StdDesc", "Manual Request")


        RefdataCategory.lookupOrCreate('Activity.Status', 'Active')
        RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
        RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')

        RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer')

        RefdataCategory.lookupOrCreate('YN', 'Yes')
        RefdataCategory.lookupOrCreate('YN', 'No')

        RefdataCategory.lookupOrCreate('DCType', 'Admin', "100")
        RefdataCategory.lookupOrCreate('DCType', 'Standard', "200")
        RefdataCategory.lookupOrCreate('DCType', 'Support', "300")

        RefdataCategory.lookupOrCreate('License.Category', 'Content')
        RefdataCategory.lookupOrCreate('License.Category', 'Software')

        RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'eMail')
        RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'HTTP Url')
        RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'FTP')
        RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'Other')

        RefdataCategory.lookupOrCreate('Source.DataFormat', 'KBART')
        RefdataCategory.lookupOrCreate('Source.DataFormat', 'Proprietary')

        RefdataCategory.lookupOrCreate('Source.Frequency', 'Daily', '001')
        RefdataCategory.lookupOrCreate('Source.Frequency', 'Weekly', '007')
        RefdataCategory.lookupOrCreate('Source.Frequency', 'Monthly', '030')
        RefdataCategory.lookupOrCreate('Source.Frequency', 'Quarterly', '090')
        RefdataCategory.lookupOrCreate('Source.Frequency', 'Yearly', '365')

        RefdataCategory.lookupOrCreate('RDFDataType', 'uri')
        RefdataCategory.lookupOrCreate('RDFDataType', 'string')

        RefdataCategory.lookupOrCreate('ingest.filetype', 'kbart2')
        RefdataCategory.lookupOrCreate('ingest.filetype', 'ingram')
        RefdataCategory.lookupOrCreate('ingest.filetype', 'ybp')
        RefdataCategory.lookupOrCreate('ingest.filetype', 'cufts')

        RefdataCategory.lookupOrCreate('Platform.Authentication', 'Unknown')

        RefdataCategory.lookupOrCreate('Platform.Roles', 'Host')

        RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')
        RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.FileAttachments')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipls')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Publisher')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Issuer')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Imprint')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.TranslatedFrom')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.AbsorbedBy')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.MergedWith')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.RenamedTo')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.SplitFrom')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstancePackagePlatform.DerivedFrom')
        RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstancePackagePlatform.MasterTipp')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.CuratoryGroups')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTitles')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.Provider')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Office.Org')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Office.CuratoryGroups')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.Imprint')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.Previous')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.Parent')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.OwnedImprints')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.CuratoryGroups')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Org.Imprint')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Provider')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.CuratoryGroups')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.NominalPlatform')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Previous')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Parent')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Vendor')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Broker')
        RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Licensor')
        RefdataCategory.lookupOrCreate('Combo.Type', 'License.Licensee')
        RefdataCategory.lookupOrCreate('Combo.Type', 'IngestionProfile.Source')
        RefdataCategory.lookupOrCreate('Combo.type', 'Source.CuratoryGroups')

        RefdataCategory.lookupOrCreate('MembershipRole', 'Administrator')
        RefdataCategory.lookupOrCreate('MembershipRole', 'Member')

        RefdataCategory.lookupOrCreate('MembershipStatus', 'Approved')
        RefdataCategory.lookupOrCreate('MembershipStatus', 'Pending')
        RefdataCategory.lookupOrCreate('MembershipStatus', 'Rejected/Revoked')

        RefdataCategory.lookupOrCreate('Price.type', 'list')
        RefdataCategory.lookupOrCreate('Price.type', 'perpetual')
        RefdataCategory.lookupOrCreate('Price.type', 'topup')
        RefdataCategory.lookupOrCreate('Price.type', 'on-off')
        RefdataCategory.lookupOrCreate('Price.type', 'subscription')

        RefdataCategory.lookupOrCreate('Currency', 'EUR')
        RefdataCategory.lookupOrCreate('Currency', 'GBP')
        RefdataCategory.lookupOrCreate('Currency', 'USD')

        RefdataCategory.lookupOrCreate('Job.Type', 'Unknown')
        RefdataCategory.lookupOrCreate('Job.Type', 'Undefined')
        RefdataCategory.lookupOrCreate('Job.Type', 'PackageCrossRef')
        RefdataCategory.lookupOrCreate('Job.Type', 'TitleCrossRef')
        RefdataCategory.lookupOrCreate('Job.Type', 'BootstrapIdentifierCleanup')
        RefdataCategory.lookupOrCreate('Job.Type', 'DepositDatafile')
        RefdataCategory.lookupOrCreate('Job.Type', 'RegenerateLicenseSummaries')
        RefdataCategory.lookupOrCreate('Job.Type', 'TidyOrgsData')
        RefdataCategory.lookupOrCreate('Job.Type', 'EnsureUUIDs')
        RefdataCategory.lookupOrCreate('Job.Type', 'EnsureTIPLs')
        RefdataCategory.lookupOrCreate('Job.Type', 'GenerateTIPPCoverage')
        RefdataCategory.lookupOrCreate('Job.Type', 'MarkInconsDateRanges')
        RefdataCategory.lookupOrCreate('Job.Type', 'UpdateFreeTextIndexes')
        RefdataCategory.lookupOrCreate('Job.Type', 'ResetFreeTextIndexes')
        RefdataCategory.lookupOrCreate('Job.Type', 'MasterListUpdate')
        RefdataCategory.lookupOrCreate('Job.Type', 'EnrichmentService')
        RefdataCategory.lookupOrCreate('Job.Type', 'GeneratePackageTypes')
        RefdataCategory.lookupOrCreate('Job.Type', 'Housekeeping')
        RefdataCategory.lookupOrCreate('Job.Type', 'CleanupDeletedComponents')
        RefdataCategory.lookupOrCreate('Job.Type', 'CleanupRejectedComponents')
        RefdataCategory.lookupOrCreate('Job.Type', 'TIPPCleanup')
        RefdataCategory.lookupOrCreate('Job.Type', 'DeleteTIWithoutHistory')
        RefdataCategory.lookupOrCreate('Job.Type', 'RejectTIWithoutIdentifier')
        RefdataCategory.lookupOrCreate('Job.Type', 'PlatformCleanup')
        RefdataCategory.lookupOrCreate('Job.Type', 'RecalculateStatistics')
        RefdataCategory.lookupOrCreate('Job.Type', 'KBARTSourceIngest')
        RefdataCategory.lookupOrCreate('Job.Type', 'KBARTIngest')
        RefdataCategory.lookupOrCreate('Job.Type', 'KBARTIngestDryRun')
        RefdataCategory.lookupOrCreate('Job.Type', 'PackageTitleMatch')
        RefdataCategory.lookupOrCreate('Job.Type', 'PackageUpdateTipps')
        RefdataCategory.lookupOrCreate('Job.Type', 'EZBCollectionIngest')

        RefdataCategory.lookupOrCreate(Office.RD_FUNCTION, 'Technical Support')
        RefdataCategory.lookupOrCreate(Office.RD_FUNCTION, 'Other')

        RefdataCategory.lookupOrCreate(CuratoryGroup.RDC_ORGA_TYPE, 'Library')
        RefdataCategory.lookupOrCreate(CuratoryGroup.RDC_ORGA_TYPE, 'Provider')

        RefdataCategory.lookupOrCreate(CuratoryGroup.RDC_ORGA_TYPE, 'Library').save(flush: true, failOnError: true)
        RefdataCategory.lookupOrCreate(CuratoryGroup.RDC_ORGA_TYPE, 'Provider').save(flush: true, failOnError: true)

        lookupOrCreateCuratoryGroupTypes()

        // Can be activated on local development instances.
        // assignMissingCGsToRRs()

        LanguagesService.initialize()

        log.debug("Deleting any null refdata values");
        RefdataValue.executeUpdate('delete from RefdataValue where value is null')
    }

    // Can be activated on local development instances.
    private void assignMissingCGsToRRs(){
        def rrsWithoutCGs = ReviewRequest.findAll()
        Iterator it = rrsWithoutCGs.iterator()
        while (it.hasNext()){
            ReviewRequest next = it.next()
            if (!CollectionUtils.isEmpty(next.getAllocatedGroups())){
                rrsWithoutCGs.remove(next)
            }
        }
        for (rr in rrsWithoutCGs){
            if (KBComponent.has(rr.componentToReview, 'curatoryGroups')){
                createArgForFirstCG(rr)
            }
        }
    }

    private void createArgForFirstCG(ReviewRequest rr){
        for (group in rr.componentToReview.curatoryGroups){
            if (group){
                new AllocatedReviewGroup(
                    group: group, review: rr, status: RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
                ).save(flush: true)
                return
            }
        }
    }

    private void lookupOrCreateCuratoryGroupTypes(){
        CuratoryGroupType journalPackageCurators = CuratoryGroupType.findByName("Journal Package Curators")
        if (journalPackageCurators){
            journalPackageCurators.setName("Package Curators")
        }
        else{
            CuratoryGroupType.findByName("Package Curators") ?:
                new CuratoryGroupType(level: CuratoryGroupType.Level.PACKAGE, name: "Package Curators")
                    .save(flush: true, failOnError: true)
        }
        CuratoryGroupType journalTitleCurators = CuratoryGroupType.findByName("Journal Title Curators")
        if (journalTitleCurators){
            journalTitleCurators.setName("Title Curators")
        }
        else{
            CuratoryGroupType.findByName("Title Curators") ?:
                new CuratoryGroupType(level: CuratoryGroupType.Level.TITLE, name: "Title Curators")
                    .save(flush: true, failOnError: true)
        }
        CuratoryGroupType journalCentralCurators = CuratoryGroupType.findByName("Journal Central Curators")
        if (journalCentralCurators){
            journalCentralCurators.setName("Central Curators")
        }
        else{
            CuratoryGroupType.findByName("Central Curators") ?:
                new CuratoryGroupType(level: CuratoryGroupType.Level.TITLE, name: "Central Curators")
                    .save(flush: true, failOnError: true)
        }
        CuratoryGroupType ebookPackageCurators = CuratoryGroupType.findByName("E-Book Package Curators")
        if (ebookPackageCurators){
            ebookPackageCurators.delete()
        }
        CuratoryGroupType ebookTitleCurators = CuratoryGroupType.findByName("E-Book Title Curators")
        if (ebookTitleCurators){
            ebookTitleCurators.delete()
        }
    }

    def sourceObjects() {
        log.debug("Lookup or create source objects")
        Source.findByName('YBP') ?: new Source(name: 'YBP').save(flush: true, failOnError: true)
        Source.findByName('CUP') ?: new Source(name: 'CUP').save(flush: true, failOnError: true)
        Source.findByName('WILEY') ?: new Source(name: 'WILEY').save(flush: true, failOnError: true)
        Source.findByName('CUFTS') ?: new Source(name: 'CUFTS').save(flush: true, failOnError: true)
        Source.findByName('ASKEWS') ?: new Source(name: 'ASKEWS').save(flush: true, failOnError: true)
        Source.findByName('EBSCO') ?: new Source(name: 'EBSCO').save(flush: true, failOnError: true)
    }


    def DSConfig() {
        DSCategory.withTransaction {
            [
                'accessdl': 'Access - Download',
                'accessol': 'Access - Read Online',
                'accbildl': 'Accessibility - Download',
                'accbilol': 'Accessibility - Read Online',
                'device'  : 'Device Requirements for Download',
                'drm'     : 'DRM',
                'format'  : 'Format',
                'lic'     : 'Licensing',
                'other'   : 'Other',
                'ref'     : 'Referencing',
            ].each { k, v ->
                def dscat = DSCategory.findByCode(k) ?: new DSCategory(code: k, description: v).save(flush: true, failOnError: true)
            }

            [
                ['format', 'Downloadable PDF', '', ''],
                ['format', 'Embedded PDF', '', ''],
                ['format', 'ePub', '', ''],
                ['format', 'OeB', '', ''],
                ['accessol', 'Book Navigation', '', ''],
                ['accessol', 'Table of contents navigation', '', ''],
                ['accessol', 'Pagination', '', ''],
                ['accessol', 'Page Search', '', ''],
                ['accessol', 'Search Within Book', '', ''],
                ['accessdl', 'Download Extent', '', ''],
                ['accessdl', 'Download Time', '', ''],
                ['accessdl', 'Download Reading View Navigation', '', ''],
                ['accessdl', 'Table of Contents Navigation', '', ''],
                ['accessdl', 'Pagination', '', ''],
                ['accessdl', 'Page Search', '', ''],
                ['accessdl', 'Search Within Book', '', ''],
                ['accessdl', 'Read Aloud or Listen Option', '', ''],
                ['device', 'General', '', ''],
                ['device', 'Android', '', ''],
                ['device', 'iOS', '', ''],
                ['device', 'Kindle Fire', '', ''],
                ['device', 'PC', '', ''],
                ['drm', 'Copying', '', ''],
                ['drm', 'Printing', '', ''],
                ['accbilol', 'Dictionary', '', ''],
                ['accbilol', 'Text Resize', '', ''],
                ['accbilol', 'Change Reading Colour', '', ''],
                ['accbilol', 'Read aloud or Listen Option', '', ''],
                ['accbilol', 'Integrated Help', '', ''],
                ['accbildl', 'Copying', '', ''],
                ['accbildl', 'Printing', '', ''],
                ['accbildl', 'Add Notes', '', ''],
                ['accbildl', 'Dictionary', '', ''],
                ['accbildl', 'Text Resize', '', ''],
                ['accbildl', 'Change Reading Colour', '', ''],
                ['accbildl', 'Integrated Help', '', ''],
                ['accbildl', 'Other Accessibility features or Support', '', ''],
                ['ref', 'Export to bibliographic software', '', ''],
                ['ref', 'Sharing / Social Media', '', ''],
                ['other', 'Changes / Redevelopment in the near future', '', ''],
                ['lic', 'Number of users', '', ''],
                ['lic', 'Credit Payment Model', '', ''],
                ['lic', 'Publishers Included', '', '']
            ].each { crit ->
                def cat = DSCategory.findByCode(crit[0]);
                if (cat) {
                    def c = DSCriterion.findByOwnerAndTitle(cat, crit[1]) ?: new DSCriterion(
                        owner: cat,
                        title: crit[1],
                        description: crit[2],
                        explanation: crit[3]).save(flush: true, failOnError: true)
                } else {
                    log.error("Unable to locate category: ${crit[0]}")
                }
            }
        }
        //log.debug(titleLookupService.getTitleFieldForIdentifier([[ns:'isbn',value:'9780195090017']],'publishedFrom'));
        //log.debug(titleLookupService.getTitleFieldForIdentifier([[ns:'isbn',value:'9780195090017']],'publishedTo'));
    }


    def registerUsers() {
        grailsApplication.config.getProperty('sysusers', List, []).each { su ->
            User.withTransaction {
                log.debug("test ${su.name} ${su.pass} ${su.display} ${su.roles}")
                def user = User.findByUsername(su.name)
                if (user) {
                    if (user.password != su.pass) {
                        log.debug("Hard change of user password from config ${user.password} -> ${su.pass}")
                        user.password = su.pass
                        user.save(failOnError: true)
                    } else {
                        log.debug("${su.name} present and correct");
                    }
                } else {
                    log.debug("Create user...")
                    user = new User(
                        username: su.name,
                        password: su.pass,
                        display: su.display,
                        email: su.email,
                        enabled: true).save(failOnError: true)
                }

                log.debug("Add roles for ${su.name}");
                su.roles.each { r ->
                    def role = Role.findByAuthority(r)
                    if (!(user.authorities.contains(role))) {
                        log.debug("  -> adding role ${role}")
                        UserRole.create user, role
                    } else {
                        log.debug("  -> ${role} already present")
                    }
                }
            }
        }
    }


    def ensureEsIndices() {
        def esClient = ESWrapperService.getClient()
        def esIndices = grailsApplication.config.getProperty('gokb.es.indices', Map, [:]).values()
        for (String indexName in esIndices) {
            ensureEsIndex(indexName, esClient)
        }
    }

    def ensureEsIndex(String indexName, def esClient) {
        log.debug("ensureESIndex for ${indexName}");
        def request = new GetIndexRequest(indexName)

        if (!esClient.indices().exists(request, RequestOptions.DEFAULT)) {
            log.debug("ES index ${indexName} did not exist, creating..")
            CreateIndexRequest createRequest = new CreateIndexRequest(indexName)
            log.debug("Adding index settings..")
            createRequest.settings(JsonOutput.toJson(ESWrapperService.getSettings().get("settings")), XContentType.JSON)

            def indexResponse = esClient.indices().create(createRequest, RequestOptions.DEFAULT)

            if (indexResponse.isAcknowledged()) {
                log.debug("Index ${indexName} successfully created!")
                PutMappingRequest mappingRequest = new PutMappingRequest(indexName).source(JsonOutput.toJson(ESWrapperService.getMapping()), XContentType.JSON)
                def mappingResponse = esClient.indices().putMapping(mappingRequest, RequestOptions.DEFAULT)

                if (mappingResponse.isAcknowledged()) {
                    log.debug("Added mapping for index")
                }
                else {
                    log.error("Unable to add mapping to new index!")
                }
            }
            else {
                log.error("Index creation failed: ${indexResponse}")
            }
        }
        else {
            log.debug("ES index ${indexName} already exists..")
            // Validate settings & mappings
        }
    }

    def registerPkgCache () {
        File dir = new File(grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory', String.class))
        File[] files = dir.listFiles()

        for (def file : files) {
            def fileNameParts = file.name.split('_')
            def pkg = Package.findByUuid(fileNameParts[0])

            if (pkg) {
                Package.executeUpdate("update Package p set p.lastCachedDate = :lcd where p.id = :pid", [lcd: new Date(file.lastModified()), pid: pkg.id])
            }
            else {
                log.warn("Unable to find package for XML cache file ${file.name}!")
            }
        }
    }
}
