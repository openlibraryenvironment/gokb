gokb
====

GOKb is an environment for managing the collaborative import, cleaning and maintaining of packages of e-resources. The focus is the supply of information around the administration and availability of print and electronic resources rather than specific bibliographic information. The goal of GOKb is to reduce the burden of data maintenance and editing on any one party by sharing the load over a collaborative network of subject matter experts. Because the information is global, and the same for everyone, the idea is to focus all our effort on editing one copy of the information that can then be freely reused and shared.

# Previous repository

GOKb development has moved here from https://github.com/k-int/gokb-phase1

# Running GOKb in grails interactive mode

## Dependencies

* Java 11
* Groovy 3.0.11 && Grails 5.3.2 (consider using sdkman)
* Clean Postgres database
* Running (local) Opensearch cluster (index will be created automatically)

## Configuration

Default configuration in `server/grails-app/conf/application.yml` can be overridden by placing an `application-development.yml` (such as the example in `template_files/`) in `server/`

As some scripts rely on the path `/gokb`, it is recommended to deploy GOKb in this path. For local development environment, add to your local `application-development.yml`:

```
server:
    port: 8080
    servlet:
     context-path: /gokb
```

## Starting the application

from `server/`
`grails run-app`
