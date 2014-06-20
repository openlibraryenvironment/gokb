import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.operation.*
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.ProjectHelper
import org.eclipse.jgit.api.errors.JGitInternalException


//Include the inbuild compile script, as we depend on compile to have been completed first.
includeTargets << grailsScript("_GrailsCompile")

// Include the package script, so we can access the config object here.
includeTargets << grailsScript('_GrailsPackage')

/**
 * Build OpenRefine
 */
target(buildRefine :"Build OpenRefine") {
  depends (compile, createConfig)
  
  // The git repo object.
  Grgit git
  
  // Try and download OpenRefine
  try {
    
    // Create the directory to house the OpenRefine project.
    File refine_repo = new File("${grailsSettings.userHome}", "${config.refine.refineRepoPath}")
    
    // The build.xml located in the OpenRefine download.
    File refine_bxml = new File (refine_repo, "${config.refine.refineBuildFile}")
    
    // OpenRefine repo.
    grailsConsole.addStatus ("Get OpenRefine")
    git = getOrCreateRepo(refine_repo, "${config.refine.refineRepoURL}")
    String branch_name = tryGettingBranch (git, config.refine.refineRepoBranch)
    String tag_name = tryGettingTag(git, config.refine.refineRepoTagPattern)
    
    // Build it.
    grailsConsole.addStatus("Attempt refine build")
    antBuild(refine_bxml, config.refine.refineBuildTarget)
    
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
  
  // Create the directory to house the OpenRefine project.
  File refine_repo = new File("${grailsSettings.userHome}", "${config.refine.refineRepoPath}")
  
  // The target to copy the directory to.
  File gokb_extension_target = new File(refine_repo, "${config.refine.gokbExtensionTarget}")
  
  // Create the directory for project containing refine extension.
  File extension_repo = new File("${grailsSettings.userHome}", "${config.refine.extensionRepoPath}")
  
  // The extension location within the repo.
  File gokb_extension = new File(extension_repo, "${config.refine.gokbExtensionPath}")
  
  // The build.xml for the GOKb extension.
  File refine_extension_bxml = new File (gokb_extension_target, "${config.refine.extensionBuildFile}")
  
  // The git repo object.
  Grgit git
  
  // Try and get the refine extension
  try {
    grailsConsole.addStatus ("Get OpenRefine GOKb extension")
    git = getOrCreateRepo(extension_repo, "${config.refine.gokbRepoURL}")
    String branch_name = tryGettingBranch (git, config.refine.gokbRepoBranch)
    String tag_name = tryGettingTag(git, config.refine.gokbRepoTagPattern)
  
    // Move the extension into the correct openrefine directory.
    grailsConsole.addStatus ("Copy the extension folder into the correct directory.")
    FileUtils.copyDirectory(
      gokb_extension,
      gokb_extension_target
    )
    
    // Attempt the build.
    grailsConsole.addStatus("Attempt extension build")
    
    // Now lets add to the properties file.
    def now = System.currentTimeMillis()
    String zip_name = "${now}-${branch_name}".toLowerCase()
    
    metadata.'extension.build.date' = "${now}" as String
    metadata.'extension.build.branch' = branch_name
    
    if (tag_name) {
      metadata.'extension.build.tag' = tag_name
      zip_name += "-${tag_name}".toLowerCase()
    }
    
    // Persist the metadata.
    metadata.persist()
    
    // We can override the build properties here.
    def build_props = ["fullname" : zip_name]
    
    antBuild(refine_extension_bxml, config.refine.extensionBuildTarget, build_props)
    
  } finally {
  
    // Release any resources.
    git?.close()
  }
  
  // Package up the extension and move into /web-app/refine/extension.zip.
  
  // Need to add to application properties.
}

private boolean antBuild (File bxml, String target = null, def props = null) {
  
  if (bxml.exists()) {
    
    grailsConsole.addStatus("build.xml found. Creating ANT project.")
    AntBuilder a = ant;
    
    // Create a project in ANT
    Project p = a.createProject()
    p.init()
    p.baseDir = bxml.getParentFile()
    
    props?.each { name, val ->
      p.setProperty("${name}", "${val}")
    }
    
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

/**
 * Try switching to a particular branch.
 * @param git
 * @param branchName
 * @return
 */
private String tryGettingBranch (Grgit git, String branchName) {
  
  if (branchName) {
  
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
  }
  
  // Return the name of the current branch.
  git.branch.current.getName()
}

/**
 * Look for a tag matching the pattern supplied.
 * @param git
 * @param tagPattern
 * @return the Grgit instance
 */
private String tryGettingTag (Grgit git, String tagPattern) {
  
  // Tag name found.
  String found_tag = null
  
  if (tagPattern) {
  
    // Grab the tags.
    List<Tag> tags = git.tag.list()
    
    // Try and find the tag for the version we want.
    Tag release = tags.find { Tag t -> t.getName() ==~ tagPattern }
    
    if (release) {
      
      found_tag = release.getName()
      
      // Found the correct tag.
      grailsConsole.addStatus ("Found tag ${found_tag}")
      
      // Reset to this tag.
      git.reset {
        mode = ResetOp.Mode.HARD
        commit = release.fullName
      }
    }
  }
  
  found_tag
}

/**
 * Pulls latest changes for existing repo or clones into directory if not already present.
 */
private Grgit getOrCreateRepo (File loc, String uri) {
  Grgit git
  
  grailsConsole.addStatus ("Checking ${loc}...")
  try {
    
    // Try opening the repo.
    git = Grgit.open(loc)
    grailsConsole.addStatus ("Found repo!")
    
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
    // SO: Suppressing this exception here. The reason is that if there are directories
    // that have come from another repository we can get an error thrown as it attempts to
    // remove the directory. Even though the error is reported the delete operation still
    // succeeds. This obviously isn't ideal, but is the only way I could see around it.
    if (!e.getCause() instanceof IOException) {
      throw e
    }
  }
  
  // Now pull the changes to the cleaned repo.
  grailsConsole.addStatus ("Pulling changes to ensure we are at the head.")
  git.pull ()
  
  git
}