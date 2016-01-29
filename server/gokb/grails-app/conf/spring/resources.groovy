// Place your Spring DSL code here
beans = {


   // Might override this to set pool size parameters
  // executorService(  grails.plugin.executor.PersistenceContextExecutorWrapper ) { bean->
  //   bean.destroyMethod = 'destroy' //keep this destroy method so it can try and clean up nicely
  //   persistenceInterceptor = ref("persistenceInterceptor")
  //    //this can be whatever from Executors (don't write your own and pre-optimize)
  //    // executor = Executors.newCachedThreadPool(new YourSpecialThreadFactory()) 
  //   executor = Executors.newFixedThreadPool(10);
  // }
}
