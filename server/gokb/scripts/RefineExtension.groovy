import org.ajoberstar.grgit.operation.*

// Include the inbuilt compile script, as we depend on compile to have been completed first.
includeTargets << grailsScript("_GrailsCompile")

// Include the package script, so we can access the config object here.
includeTargets << grailsScript('_GrailsPackage')

/**
 * Build OpenRefine
 */
target(buildRefine :"Build OpenRefine") {
  depends (compile, createConfig)
  
  RefineUtils = classLoader.loadClass("com.k_int.RefineUtils")
  GitUtils = classLoader.loadClass("com.k_int.grgit.GitUtils")
  
  // Create the directory to house the OpenRefine project.
  File refine_repo = new File("${grailsSettings.userHome}", "${config.refine.refineRepoPath}")
  
  // The build.xml located in the OpenRefine download.
  File refine_bxml = new File (refine_repo, "${config.refine.refineBuildFile}")
  
  // Monitor for progress report.
  def monitor = classLoader.loadClass("com.k_int.grgit.GConsoleMonitor").newInstance(grailsConsole)
  
  // Do the build.
  RefineUtils.buildRefine(
    config.refine.refineRepoURL,
    refine_repo,
    refine_bxml,
    config.refine.refineBuildTarget,
    ant,
    config.refine.refineRepoBranch,
    config.refine.refineRepoTagPattern,
    monitor
  )
}

/**
 * The default target.
 */
target(default: "The default target") {
  depends(packageExtension)
}

/**
 * Package the extension up and add to the web-app/refine folder
 */
target(packageExtension : "Package up the extension and add to the app directory.") {
  
  // Ensure the extension is built.
  depends(buildExtension)
  
  RefineUtils.copyZip(ant, "${refine_package}", "${basedir}/web-app/refine")
}

/**
 * Build the GOKb Extension.
 */
target(buildExtension:"Build Extension") {
  
  depends (buildRefine)
  
  // Create the directory to house the OpenRefine project.
  File refine_repo = new File("${grailsSettings.userHome}", "${config.refine.refineRepoPath}")
  
  // The target to copy the directory to.
  File gokb_extension_target = new File(refine_repo, "${config.refine.gokbExtensionTarget}")
  
  // Create the directory for project containing refine extension.
  File extension_repo = new File("${grailsSettings.userHome}", "${config.refine.extensionRepoPath}")
  
  // The extension location within the repo.
  File gokb_extension_path = new File(extension_repo, "${config.refine.gokbExtensionPath}")
  
  // The build.xml for the GOKb extension.
  File refine_extension_bxml = new File (gokb_extension_target, "${config.refine.extensionBuildFile}")
  
  // Create a monitor for this long process.
  def monitor = classLoader.loadClass("com.k_int.grgit.GConsoleMonitor").newInstance(grailsConsole)
  
  def entries = RefineUtils.buildGOKbRefineExtension(
    config.refine.gokbRepoURL,
    extension_repo,
    refine_extension_bxml,
    config.refine.extensionBuildTarget,
    refine_repo,
    gokb_extension_path,
    gokb_extension_target,
    config.refine.gokbRepoTagPattern,
    ant,
    config.refine.gokbRepoBranch,
    config.refine.gokbRepoTagPattern,
    monitor
  )
  
  // Add to the metadata.
  if (entries) {
    
    // Set the location of the built zip file that contains the extension.
    refine_package = entries.remove("refine_package")
    
    updateMetadata(entries)
  }
}

