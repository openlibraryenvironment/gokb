

# Initial Setup

## Database

Default commands to set up pgsql for gokb follow:

    sudo -u postgres psql postgres


    psql (9.5.4)
    Type "help" for help.

    postgres=# CREATE DATABASE gokb;
    CREATE DATABASE
    postgres=# CREATE USER knowint WITH PASSWORD 'knowint';
    CREATE ROLE
    postgres=# GRANT ALL PRIVILEGES ON DATABASE gokb to knowint;
    GRANT
    \q


Connect with

psql -h localhost -U knowint gokb

## Config

There are several config values without a default value in `application.yml` which you may want to set for development/testing.

```
environments:
  development:
    serverURL: "http://localhost:8080/gokb"
    server:
      servlet:
        context-path: /gokb
    gokb:
      uiUrl: # Base URL of alternative UI application (see openlibraryenvironment/gokb-ui)
      support:
        emailTo: # Contact email address communicated to users in emails
        locale: # Locale String determining default language of support emails
      alerts:
        emailFrom: # Sender information ('from') for mails distributed by the system
      centralGroups:
        JournalInstance: # Default curatory group for handling review requests for specific types of title instances
    grails:
      plugin:
        springsecurity:
          rest:
            token:
              storage:
                jwt:
                  secret: # Secret for generating token used in bearer-based /rest endpoints
```

# Testing

In order to test in an environment comparable to deployment, gokb uses postgres as its test database.
Note that the test configuration for integration and functional tests are create-drop meaning that the DB is dropped after the tests have run.
