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
    
    /**
     * Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel()
     */
    public boolean cancel () {
      task.get
      task.cancel false
    }
    
    /**
     * Attempt to force Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel(boolean mayInterruptIfRunning)
     */
    public boolean forceCancel () {
      task.cancel true
    }
    
    /**
     * See if the job is done.
     * @see java.util.concurrent.FutureTask#done()
     */
    public boolean isDone () {
      task.done
    }
    
    /**
     * Starts the background task. 
     * @return this Job
     */
    public Job startOrQueue () {
      
      // Check for a parameter on this closure.
      work = work.rcurry(this)      
      task = executorService.submit(work)
      this
    }
    
    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get()
     */
    public def get() {
      task.get()
    }
    
    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get(long timeout, TimeUnit unit)
     */
    public def get(long time, TimeUnit unit) {
      task.get(time, unit)
    }
  }
  
  // Store each job hashed by ID. ConcurrentHashMap is thread-safe and, with only one thread updating per entry,
  // should perform well enough.
  private Map<Integer, Job> map = new ConcurrentHashMap<Integer, Job>().withDefault { int the_id ->
    new Job (["id" : the_id])
  }
  
  /**
   * Executor Service
   */
  PersistenceContextExecutorWrapper executorService
  static scope = "singleton"

  
  /**
   * Creates a new job that will execute the supplied closure in a background thread.
   * @param task
   * @return a new Job
   */
  public Job createJob (Closure task) {
    
    // Just allocate the job ID to the size of the map.
    Job j = createNewJob()
    
    // Add the work payload.
    j.work = task
    
    // Return the job.
    j
  }
  
  /**
   * Creates a new job and adds it to the map.
   * @return a new Job
   */
  private Job createNewJob() {
    
    // The job id.
    int jobid = map.size() + 1
    while (this.map.containsKey(jobid)) {
      jobid ++
    }
    
    Job j = map.get(jobid)
    j
  }
  
  /**
   * Gets the Job matching the supplied ID. 
   * @param job_id
   * @return the Job
   */
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
