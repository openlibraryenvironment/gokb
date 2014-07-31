package com.k_int

import grails.plugin.executor.PersistenceContextExecutorWrapper
import grails.transaction.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * This service will allocate tasks to the Executor service while maintaining a list of current tasks
 * and information about their completeness. 
 * 
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 */

@Transactional
class ConcurrencyManagerService {
  
  private class Job {
    int id
    private FutureTask task
    int progress
    Date startTime
    private Closure work
    
    public boolean cancel () {
      task.get
      task.cancel false
    }
    
    public boolean forceCancel () {
      task.cancel true
    }
    
    public boolean isDone () {
      task.done
    }
    
    public Job startOrQueue () {
      
      // Check for a parameter on this closure.
      if (work.parameterTypes?.length > 0) {
        work = work.rcurry(this)
      }
      
      task = executorService.submit(work)
      this
    }
    
    public def get() {
      task.get()
    }
    
    public def get(long time, TimeUnit unit) {
      task.get(time, unit)
    }
  }
  
  // Store data about each job.
  private Map<Integer, Job> map = new ConcurrentHashMap<Integer, Job>().withDefault { int the_id ->
    new Job (["id" : the_id])
  }
  
  /**
   * Executor Service
   */
  PersistenceContextExecutorWrapper executorService
  static scope = "singleton"

  public Job createTask (Closure task) {
    
    // Just allocate the job ID to the size of the map.
    Job j = createNewJob()
    
    // Add the work payload.
    j.work = task
    
    // Return the job.
    j
  }
  
  private Job createNewJob() {
    
    // The job id.
    int jobid = map.size() + 1
    while (this.map.containsKey(jobid)) {
      jobid ++
    }
    
    Job j = map.get(jobid)
    j
  }
  
  public Job getJob(int job_id){
    if (job_id == null || !this.map.containsKey(job_id)) {
      return null
    }
    
    // Get the job.
    Job j = map.get (job_id)
    
    // Check if the job has finished.
    if (j.isDone()) {
      // Remove from the map too as we don't need to keep track any more.
      map.remove (job_id)
    }
    
    // Return the job.
    j
  }
}
