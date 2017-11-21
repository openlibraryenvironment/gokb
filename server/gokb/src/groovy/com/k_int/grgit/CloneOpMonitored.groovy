package com.k_int.grgit

import org.ajoberstar.grgit.Credentials
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.auth.TransportOpUtil
import org.ajoberstar.grgit.exception.GrgitException
import org.ajoberstar.grgit.operation.CloneOp
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ProgressMonitor

class CloneOpMonitored extends CloneOp {
  
  ProgressMonitor monitor
  
  Grgit call() {
    if (!checkout && refToCheckout) {
      throw new IllegalArgumentException('Cannot specify a refToCheckout and set checkout to false.')
    }

    CloneCommand cmd = Git.cloneRepository()
    TransportOpUtil.configure(cmd, credentials)

    cmd.directory = dir
    cmd.uri = uri
    cmd.remote = remote
    cmd.bare = bare
    cmd.noCheckout = !checkout
    if (refToCheckout) { cmd.branch = refToCheckout }
    
    // Add the monitor
    cmd.setProgressMonitor(monitor)

    try {
      cmd.call()
      return Grgit.open(dir, credentials)
    } catch (GitAPIException e) {
      throw new GrgitException('Problem cloning repository.', e)
    }
  }
}