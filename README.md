gokb
====

GOKb is an environment for managing the collaborative import, cleaning and maintaining of packages of e-resources. The focus is the supply of information around the administration and availability of print and electronic resources rather than specific bibliographic information. The goal of GOKb is to reduce the burden of data maintenance and editing on any one party by sharing the load over a collaborative network of subject matter experts. Because the information is global, and the same for everyone, the idea is to focus all our effort on editing one copy of the information that can then be freely reused and shared.

# Previous repository

GOKb development has moved here from https://github.com/k-int/gokb-phase1

# Running GOKb in grails interactive mode

## Dependencies

* Clean Postgres database
* Running Elasticsearch cluster (index will be created automatically)

## Configuration

Default configuration in `gokb/server/gokbg3/grails-app/conf/application.yml` can be overridden by placing an `application-development.yml` (such as the example in `template_files/`) in `gokb/server/gokbg3/`

As some scripts rely on the path `/gokb`, it is recommended to deploy GOKb in this path. For local development environment, add to your local `application-development.yml`:

```
server:
    port: 8080
    contextPath: /gokb
```

## Starting the application

from `gokb/server/gokbg3/`
`grails run-app`


# Orgs Import

A script is provided to import the ncsu orgs list, run from `gokb/scripts/import/orgs/` as follows:

`./ncsu_orgs_imp.groovy ./ncsu-auth-orgs-roles-2013-01-11.csv`






For parsing out Platform URLs
http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/net/InternetDomainName.html
`System.out.println(InternetDomainName.fromLenient(uriHost).topPrivateDomain().name());`
