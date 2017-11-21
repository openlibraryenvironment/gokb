// Place your Spring DSL code here
import grails.plugin.executor.PersistenceContextExecutorWrapper
import grails.util.Holders;

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import org.codehaus.groovy.grails.commons.GrailsApplication

GrailsApplication grailsApplication = Holders.grailsApplication

beans = {
  def pools = grailsApplication.config?.concurrency?.pools
  if (pools) {
    ExecutorService pool
    
    // Handle custom pools.
    pools.each { k, v ->
      println "Adding pool ${k}."
      try {
        
        def conf = v."conf" as Object[]
        def method = "new${v.type}"
        
        if (method != "new" && Executors.metaClass.methods.find {it.name == method}) {
          println "Type defined is correct."
          
          // Registering new bean.
          "${k}ExecutorService" (PersistenceContextExecutorWrapper) { bean ->
            bean.destroyMethod = 'destroy'
            persistenceInterceptor = ref("persistenceInterceptor")
            executor = Executors.newCachedThreadPool()
          }
          println "Succesfully added ${k}ExecutorService."
          
        } else {
          println "Can't find Executors.${method}"
        }
      } catch( Exception e ) {
        // if anything goes wrong just stop.
        println "Error occured creating custom pool"
        e.printStackTrace()
      }
    }
  }
}
