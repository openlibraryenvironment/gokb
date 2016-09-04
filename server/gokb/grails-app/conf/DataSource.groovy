dataSource {
    pooled = true
    driverClassName = "org.h2.Driver"
    username = "sa"
    password = ""
}
hibernate {
  cache.use_second_level_cache = true
  cache.use_query_cache = false
  cache.region.factory_class = 'net.sf.ehcache.hibernate.EhCacheRegionFactory'
}
// environment specific settings
environments {

    development {
        dataSource {
            dbCreate = "update"
            url = "jdbc:postgresql://pghost:5432/gokbdev"
            username = "knowint"
            password = "knowint"
            driverClassName = "org.postgresql.Driver"
            dialect = org.hibernate.dialect.PostgreSQLDialect
            defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            pooled = true
            // logSql = true
            // formatSql = true
            configClass = 'com.k_int.KIGormConfiguration'
            properties {
                maxActive = 500
                minEvictableIdleTimeMillis=1800000
                timeBetweenEvictionRunsMillis=1800000
                numTestsPerEvictionRun=3
                testOnBorrow=true
                testWhileIdle=true
                testOnReturn=true
                validationQuery="select 1"
                defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
                // defaultTransactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE
            }
        }
    }
    test {
        dataSource {
            dbCreate = "create-drop"
            url = "jdbc:postgresql://pghost:5432/ebookstest"
            username = "knowint"
            password = "knowint"
            driverClassName = "org.postgresql.Driver"
            dialect = org.hibernate.dialect.PostgreSQLDialect
            defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            pooled = true
            // logSql = true
            // formatSql = true
            configClass = 'com.k_int.KIGormConfiguration'
            properties {
                maxActive = 500
                minEvictableIdleTimeMillis=1800000
                timeBetweenEvictionRunsMillis=1800000
                numTestsPerEvictionRun=3
                testOnBorrow=true
                testWhileIdle=true
                testOnReturn=true
                validationQuery="select 1"
                defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
                // defaultTransactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE
            }
        }
    }
    production {
        dataSource {
            dbCreate = "update"
            url = "jdbc:postgresql://pghost:5432/gokb"
            username = "knowint"
            password = "knowint"
            driverClassName = "org.postgresql.Driver"
            dialect = org.hibernate.dialect.PostgreSQLDialect
            defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            pooled = true
            // logSql = true
            // formatSql = true
            properties {
                maxActive = 500
                minEvictableIdleTimeMillis=1800000
                timeBetweenEvictionRunsMillis=1800000
                numTestsPerEvictionRun=3
                testOnBorrow=true
                testWhileIdle=true
                testOnReturn=true
                validationQuery="select 1"
                defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            }
        }
    }
}
