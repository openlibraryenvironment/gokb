package com.k_int.grgit

import grails.build.logging.GrailsConsole
import org.eclipse.jgit.lib.ProgressMonitor

class GConsoleMonitor implements ProgressMonitor {
  
  // The console.
  GrailsConsole console
  
  int task_counter
  int total_tasks
  String current_task_name
  int current_task_total
  int current_task_progress
  long start_time
  
  GConsoleMonitor (GrailsConsole console) {
    this.console = console
    task_counter = 0
  }

  @Override
  public void beginTask (String task_name, int total_work) {
    current_task_name = task_name
    current_task_total = total_work
    current_task_progress = 0
    
    if (current_task_total > 0) task_counter ++
    
    console.addStatus(
      (task_counter > 0 ? "${task_counter}/${total_tasks} " : "") +
      "${current_task_name}..." +
      (current_task_total > 0 ? " 0 of ${current_task_total}" : "")
    )
  }

  @Override
  public void endTask () {
    // Update console.
    console.updateStatus("${current_task_name} Completed")
    
    if (task_counter == total_tasks) {
      // Add finished message.
      console.addStatus("Process finished in ${((System.currentTimeMillis() - start_time)/1000)} seconds")
    }
  }

  @Override
  public boolean isCancelled () {
    // Always return false. Cannot be user cancelled.
    false
  }

  @Override
  public void start (int total_tasks) {
    start_time = System.currentTimeMillis()
    total_tasks = total_tasks
  }

  @Override
  public void update (int increment) {
    current_task_progress += increment
    console.updateStatus("${current_task_name} ${current_task_progress}" + (current_task_total > 0 ? " of ${current_task_total}" : ""))
  }
}
