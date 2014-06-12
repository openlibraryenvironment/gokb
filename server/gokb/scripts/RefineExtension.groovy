import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.ajoberstar.grgit.operation.*
import org.eclipse.jgit.lib.TextProgressMonitor

includeTargets << grailsScript("_GrailsCompile")
target(default:"Compile the client extension") {
  depends (compile)

  // Create the directory to house the OpenRefine project.
  File refine_repo = new File("${grailsSettings.userHome}", "build-refine")
  
  // Create the directory for project containing refine extension.
  File extension_repo = new File("${grailsSettings.userHome}", "refine-extension")
  
  // OpenRefine repo.
  grailsConsole.addStatus ("Get latest OpenRefine")
  Grgit refine = getOrCreateRepo(refine_repo, "https://github.com/OpenRefine/OpenRefine.git")
  
//  def tags = refine.tag.list()
//  grailsConsole.addStatus ("Found tags ${ tags }")
  
  // Move the extension into the correct openrefine directory.
  // Build OpenRefine using ANT.
  // Package up the extension and move into /web-app/refine/extension.zip.
  
  // Need to add to application properties.
}

private Grgit getOrCreateRepo (File loc, String uri) {
  Grgit git
  try {
    
    // Try opening the repo.
    git = Grgit.open(loc)
    grailsConsole.addStatus ("Found repo at ${loc}. Reset needed.")
    
    // Now fetch and hard reset the head.
    git.fetch {
      tagMode = FetchOp.TagMode.ALL
      prune   = true
    }
    git.reset {
      mode = ResetOp.Mode.HARD
    }
    
  } catch (RepositoryNotFoundException e) {
  
    // Clone the repo.
    grailsConsole.addStatus ("No repo found. Cloning from ${uri}")
    
    Class c = classLoader.loadClass("com.k_int.grgit.MonitoredGit")
    def monitor = classLoader.loadClass("com.k_int.grgit.GConsoleMonitor").newInstance(grailsConsole)
    c.monitor = monitor
    
    git = c.cloneMonitored(dir: loc, uri: (uri))
  }
  
  git
}