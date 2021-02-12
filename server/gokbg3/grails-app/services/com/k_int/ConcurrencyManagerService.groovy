package com.k_int

import org.gokb.cred.JobResult
import org.gokb.cred.RefdataValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import org.grails.async.factory.future.CachedThreadPoolPromiseFactory

import grails.async.Promise
import grails.async.PromiseFactory
import grails.async.Promises
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional

/**
 * This service will allocate tasks to the Executor service while maintaining a list of current tasks
 * and information about their completeness.
 *
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 */

@Transactional
class ConcurrencyManagerService {

  def executorService

  private static final Map<String, PromiseFactory> pools

  static {
    // Set the default promise factory and limit to 100 threads.
    Promises.setPromiseFactory(
      new CachedThreadPoolPromiseFactory(100, 60L, TimeUnit.SECONDS)
    )

    // Immutable pool map.
    pools = Collections.unmodifiableMap (['smallJobs' : new CachedThreadPoolPromiseFactory(1, 60L, TimeUnit.SECONDS)])
  }


  public class Job implements Promise, Future {
    String uuid
    private Promise task
    private Closure work
    int progress
    Date startTime = new Date()
    Date endTime
    boolean begun = false;
    String description
    List messages = []
    Map linkedItem
    RefdataValue type
    int ownerId
    int groupId

    public message(String message) {
      log.debug(message);
      messages.add([timestamp:System.currentTimeMillis(), message:message]);
    }

    public message(Map message) {
      log.debug("${message}");
      messages.add(message)
    }

    public getMessages() {
      return messages
    }

    /**
     * Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel(boolean)
     */
    public synchronized boolean cancel () {
      cancel (false)
     }

    /**
     * Attempt to force Cancel the job.
     * @see java.util.concurrent.FutureTask#cancel(boolean mayInterruptIfRunning)
     */
    public synchronized boolean forceCancel () {
      cancel(true)
    }

    /**
     * See if the job is done.
     * @see java.util.concurrent.FutureTask#done()
     */
    @Override
    public synchronized boolean isDone () {
      this.task.done
    }

    @Override
    public Job accept(value) {
      this.task.accept(value)
      this
    }

    @Override
    public Job onComplete(Closure callable) {
      this.task.onComplete(callable)
      this
    }

    @Override
    public Job onError(Closure callable) {
      this.task.onComplete(callable)
      this
    }

    @Override
    public Job then(Closure callable) {
      this.task.onComplete(callable)
      this
    }

    @Override
    public boolean cancel (boolean mayInterruptIfRunning) {
      this.task.cancel(mayInterruptIfRunning)
      message("cancel Job ($uuid)")
      endTime = new Date()
    }

    @Override
    public boolean isCancelled () {
      this.task.isCancelled();
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

        task = Promises.task(work as Closure)

        begun = true
        startTime = new Date()
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
        task = ConcurrencyManagerService.pools.get ("${poolName}").createPromise(work as Closure)

        begun = true
        startTime = new Date()
      }

      this
    }

    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get()
     * II: I don't think this should be synchronized, as a waiting process will essentially block any other
     * activities on this monitor. Removed for test..
     */
    @Override
    public def get() {
      return task.get()
    }

    /**
     * Attempt to retrieve the result. May be Null
     * @see java.util.concurrent.FutureTask#get(long timeout, TimeUnit unit)
     */
    @Override
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
  private Map<String, Job> map = new ConcurrentHashMap<String, Job>().withDefault { String the_id ->
    new Job (["uuid" : the_id])
  }

  public Map<String, Job> getJobs() {
    return new ConcurrentHashMap<String, Job>(map)
  }

  GrailsApplication grailsApplication

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
    String jobid = UUID.randomUUID().toString()

    Job j = map.get(jobid)
    j
  }

  /**
   * Gets the Job matching the supplied ID.
   * @param job_id
   * @return the Job
   */
  public Job getJob(String job_id, boolean cleanup = false) {
    if (job_id == null || !this.map.containsKey(job_id)) {
      return null
    }

    // Get the job.
    Job j = map.get (job_id)

    // Check if the job has finished.
    if (j.isDone() && cleanup) {
      // Remove from the map too as we don't need to keep track any more.
      map.remove (job_id)
    }

    // Return the job.
    j
  }

  /**
   * Gets all Jobs for the supplied User id.
   * @param user_id
   * @param max
   * @param offset
   * @return List of Jobs
   */
  public Map getUserJobs(int user_id, int max, int offset) {
    def allJobs = getJobs()
    def selected = []
    def result = [:]
    def total = null

    if (user_id == null) {
      return null
    }

    // Get the jobs.
    allJobs.each { k, v ->
      if (v.ownerId == user_id) {
        selected << [
          uuid: v.uuid,
          progress: v.progress,
          messages: v.messages,
          description: v.description,
          type: v.type ? [id: v.type.id, name: v.type.value, value: v.type.value] : null,
          begun: v.begun,
          linkedItem: v.linkedItem,
          startTime: v.startTime,
          endTime: v.endTime,
          cancelled: v.isCancelled()
        ]
      }
    }

    total = selected.size()

    if (offset > 0) {
      selected = selected.drop(offset)
    }

    result.data = selected.take(max)

    result._pagination = [
      total: total,
      limit: max,
      offset: offset
    ]

    // Return the jobs.
    result
  }

  /**
   * Gets all Jobs for the supplied CuratoryGroup id.
   * @param group_id
   * @param max
   * @param offset
   * @return List of Jobs
   */
  public Map getGroupJobs(int group_id, int max = 10, int offset = 0) {
    def allJobs = getJobs()
    def selected = []
    def result = [:]
    def total = null

    if (group_id == null) {
      return null
    }

    log.debug("Getting jobs for group ${group_id}")

    // Get the jobs.
    allJobs.each { k, v ->
      if (v.groupId == group_id) {
        selected << [
          uuid: v.uuid,
          progress: v.progress,
          messages: v.messages,
          description: v.description,
          type: v.type ? [id: v.type.id, name: v.type.value, value: v.type.value] : null,
          begun: v.begun,
          linkedItem: v.linkedItem,
          startTime: v.startTime,
          endTime: v.endTime,
          cancelled: v.isCancelled()
        ]
      }
    }

    total = selected.size()

    if (offset > 0) {
      selected = selected.drop(offset)
    }

    result.data = selected.take(max)

    result._pagination = [
      total: total,
      limit: max,
      offset: offset
    ]

    // Return the jobs.
    result
  }

  public JobResult persistJobResult(Job j, def result_object = null) {
    def jobResult = JobResult.findByUuid(j.uuid)

    if (!jobResult) {
      log.debug("Persisting Job result for Job '${j.description}'")
      if (!result_object) {
        result_object = j.get()
      }

      def job_map = [
        uuid: (j.uuid),
        description: (j.description),
        resultObject: (result_object as JSON).toString(),
        type: (j.type),
        statusText: (result_object.result),
        ownerId: (j.ownerId),
        groupId: (j.groupId),
        startTime: (j.startTime),
        endTime: (j.endTime),
        linkedItemId: (j.linkedItem?.id)
      ]

      jobResult = new JobResult(job_map).save(flush: true, failOnError: true)
    }
    jobResult
  }

  @javax.annotation.PreDestroy
  def destroy() {
  }
}
