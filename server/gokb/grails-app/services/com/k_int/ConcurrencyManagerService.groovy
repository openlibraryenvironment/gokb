package com.k_int

import grails.plugin.executor.PersistenceContextExecutorWrapper
import grails.transaction.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.Callable
import org.codehaus.groovy.grails.commons.GrailsApplication

/**
 * This service will allocate tasks to the Executor service while maintaining a list of current tasks
 * and information about their completeness.
 *
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 */

@Transactional
class ConcurrencyManagerService {

  public class Job {
    int id
    private FutureTask task
    int progress
    Date startTime
    Date endTime
    private Closure work
    boolean begun = false;
    String description
    List messages = []

    public message(String message) {
      log.debug(message);
      messages.add([timestamp:System.currentTimeMillis(), message:message]);
    }

    public message(Map message) {
      log.debug(message);
      messages.add(message)
    }

    public getMessages() {
      return messages
    }

    /**
     * Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel()
     */
    public synchronized boolean cancel () {
      task.cancel false
    }

    /**
     * Attempt to force Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel(boolean mayInterruptIfRunning)
     */
    public synchronized boolean forceCancel () {
      task.cancel true
    }

    /**
     * See if the job is done.
     * @see java.util.concurrent.FutureTask#done()
     */
    public synchronized boolean isDone () {
      task.done
    }

    /**
     * Starts the background task.
     * @return this Job
     */
    public synchronized Job startOrQueue () {

      // Just return if this task has already started.
      if (!begun) {

        // Check for a parameter on this closure.
        work = work.rcurry(this)

        task = executorService.submit(work as Callable<Date>)

        begun = true
      }

      this
    }
    
    /**
     * Starts the background task with a named pool.
     * @return this Job
     */
    public synchronized Job startOrQueue (String poolName) {

      // Just return if this task has already started.
      if (!begun) {

        // Check for a parameter on this closure.
        work = work.rcurry(this)

        task = grailsApplication.mainContext."${poolName}ExecutorService".submit(work as Callable<Date>)

        begun = true
      }

      this
    }

    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get()
     * II: I don't think this should be synchronized, as a waiting process will essentially block any other
     * activities on this monitor. Removed for test..
     */
    public def get() {
      return task.get()
    }

    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get(long timeout, TimeUnit unit)
     */
    public synchronized def get(long time, TimeUnit unit) {
      return task.get(time, unit)
    }

    public synchronized def setProgress( progress, total) {
      this.progress = ( progress.div(total) * 100 )
    }

    public synchronized def setProgress(int progress) {
      this.progress = progress
    }
  }

  // Store each job hashed by ID. ConcurrentHashMap is thread-safe and, with only one thread updating per entry,
  // should perform well enough.
  private Map<Integer, Job> map = new ConcurrentHashMap<Integer, Job>().withDefault { int the_id ->
    new Job (["id" : the_id])
  }

  public Map<Integer, Job> getJobs() {
    return map;
  }

  GrailsApplication grailsApplication
  
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
