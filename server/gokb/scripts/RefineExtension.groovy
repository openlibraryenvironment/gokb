import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.operation.*
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.ProjectHelper
import org.eclipse.jgit.api.errors.JGitInternalException

/**
 * Include the inbuild compile script, as we depend on compile to have been completed first.
 */
includeTargets << grailsScript("_GrailsCompile")

/**
 * Various files and folders needed for build process.
 */
// Create the directory to house the OpenRefine project.
File refine_repo = new File("${grailsSettings.userHome}", "gokb-build/refine")

// Create the directory for project containing refine extension.
File extension_repo = new File("${grailsSettings.userHome}", "gokb-build/extension")

// The extension location within the repo.
File gokb_extension = new File(extension_repo, "refine/extensions/gokb")

// The target to copy the directory to.
File gokb_extension_target = new File(refine_repo, "extensions/gokb/")

// The build.xml located in the OpenRefine download.
File refine_bxml = new File (refine_repo, 'build.xml')

// The build.xml for the GOKb extension.
File refine_extension_bxml = new File (gokb_extension_target, 'build.xml')



/**
 * Build OpenRefine
 */
target(buildRefine :"Build OpenRefine") {
  depends (compile)
  
  // The git repo object.
  Grgit git
  
  // Try and download OpenRefine
  try {
    
    // OpenRefine repo.
    grailsConsole.addStatus ("Get OpenRefine")
    git = getOrCreateRepo(refine_repo, "https://github.com/OpenRefine/OpenRefine.git")
    git = tryGettingTag(git, "2.6-beta.1")
    
    // Build it.
    grailsConsole.addStatus("Attempt refine build")
    antBuild(refine_bxml)
    
  } finally {
  
    // Release any resources.
    git?.close()
  }
}

/**
 * Build the GOKb Extension.
 */
target(default:"Build Extension") {
  depends (buildRefine)
  
  // The git repo object.
  Grgit git
  
  // Try and get the refine extension
  try {
    grailsConsole.addStatus ("Get OpenRefine GOKb extension")
    git = getOrCreateRepo(extension_repo, "https://github.com/k-int/gokb-phase1.git")
    git = tryGettingBranch (git, "feature-extension_improvements")
//    git = tryGettingTag(git, "release3")
  
    // Move the extension into the correct openrefine directory.
    grailsConsole.addStatus ("Copy the extension folder into the correct directory.")
    FileUtils.copyDirectory(
      gokb_extension,
      gokb_extension_target
    )
    
    // Attempt the build.
    grailsConsole.addStatus("Attempt extension build")
    antBuild(refine_extension_bxml, 'dist')
    
  } finally {
  
    // Release any resources.
    git?.close()
  }
  
  // Package up the extension and move into /web-app/refine/extension.zip.
  
  // Need to add to application properties.
}

private boolean antBuild (File bxml, String target = null) {
  
  if (bxml.exists()) {
    
    grailsConsole.addStatus("build.xml found. Creating ANT project.")
    AntBuilder a = ant;
    
    // Create a project in ANT
    Project p = a.createProject()
    p.init()
    p.baseDir = bxml.getParentFile()
    
    // Configure the project using the build file.
    ProjectHelper.projectHelper.parse(p, bxml)
    
    // Execute default target.
    grailsConsole.addStatus("Using ${p.baseDir} as base directory")
    
    // Execute Default target.
    p.executeTarget(target ?: p.defaultTarget)
    return true
    
  } else {
    grailsConsole.addStatus("build.xml not found")
  }
  return false
}

private Grgit tryGettingBranch (Grgit git, String branchName) {
  
  // Grab the branches.
  List<Branch> branches = git.branch.list {
    mode = BranchListOp.Mode.LOCAL
  }
  
  // Search for a matching branch
  Branch the_branch = branches.find { it.getName() == branchName }
  
  if (the_branch) {
    grailsConsole.addStatus ("Found branch ${the_branch.getName()}")
    
    // Checkout the branch.
    git.checkout {
      branch = (the_branch.fullName)
    }
    grailsConsole.addStatus ("Checked out")
  }
  
  git
}

private Grgit tryGettingTag (Grgit git, String tagName) {
  
  // Grab the tags.
  List<Tag> tags = git.tag.list()
  
  // Try and find the tag for the version we want.
  Tag release = tags.find { Tag t -> t.getName() == tagName }
  
  if (release) {
    
    // Found the correct tag.
    grailsConsole.addStatus ("Found tagged release ${release.getName()}")
    
    // Reset to this tag.
    git.reset {
      mode = ResetOp.Mode.HARD
      commit = release.fullName
    }
    
    grailsConsole.addStatus ("Reset to tag.")
  }
  
  git
}

private Grgit getOrCreateRepo (File loc, String uri) {
  Grgit git
  try {
    
    // Try opening the repo.
    git = Grgit.open(loc)
    grailsConsole.addStatus ("Found repo at ${loc}.")
    
  } catch (RepositoryNotFoundException e) {
  
    // Clone the repo.
    grailsConsole.addStatus ("No repo found. Cloning from ${uri}")
    
    // SO: This looks odd but the only way we can include a 
    Class c = classLoader.loadClass("com.k_int.grgit.MonitoredGit")
    def monitor = classLoader.loadClass("com.k_int.grgit.GConsoleMonitor").newInstance(grailsConsole)
    c.monitor = monitor
    
    git = c.cloneMonitored(dir: loc, uri: (uri))
  }
  
  // Now fetch everything from the remote and ensure the local is in sync.
  grailsConsole.addStatus ("Fetching all branches.")
  git.fetch {
    tagMode = FetchOp.TagMode.ALL
    prune   = true
    refSpecs = ["*:*"]
  }
  
  // Clean to remove all none-tracked changes.
  grailsConsole.addStatus ("Cleaning repo.")
  try {
    git.clean {
      directories = true
      ignore = false
    }
  } catch (JGitInternalException e) {
    // Suppress this here.
    if (!e.getCause() instanceof IOException) {
      throw e
    }
  }
  
  // Now pull the cahanges to the cleaned repo.
  grailsConsole.addStatus ("Pulling changes to ensure we are at the head.")
  git.pull ()
  
  git
}