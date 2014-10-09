package com.k_int

import grails.build.logging.GrailsConsole
import groovy.util.logging.Log4j

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.operation.*
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.ProjectHelper
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor

import com.k_int.grgit.GitUtils

@Log4j
class RefineUtils {

  public static Project buildRefine(String remote_uri, File local_repo, File bxml, String b_target, AntBuilder ant = new AntBuilder(), String branch = null, String tag = null, ProgressMonitor monitor = NullProgressMonitor.INSTANCE) {

    // The git repo object.
    Grgit git

    // Try and download OpenRefine
    try {

      // OpenRefine repo.
      log.debug ("Get OpenRefine")
      git = GitUtils.getOrCreateRepo(local_repo, remote_uri, monitor)
      
      // Get the branch.
      String branch_name = GitUtils.tryGettingBranch (git, branch)
      String tag_name = GitUtils.tryGettingTag(git, tag)

      // Build it.
      log.debug ("Attempt refine build from ${bxml} using target ${b_target}")
      return antBuild(bxml, ant, b_target)

    } finally {

      // Release any resources.
      git?.close()
    }
  }
  
  /**
   * Build the GOKb Extension.
   */
  public static Map buildGOKbRefineExtension(String remote_uri, File local_repo, File bxml, String b_target, File ref_folder, File ext_folder, File target_ext_folder, String tag_matcher_pattern, AntBuilder ant = new AntBuilder(), String branch = null, String tag = null, ProgressMonitor monitor = NullProgressMonitor.INSTANCE) {
    
    // The git repo object.
    Grgit git
    
    def entries = [:]
      
    // Try and get the refine extension
    try {
      
      log.debug ("Get OpenRefine GOKb extension")
      git = GitUtils.getOrCreateRepo(local_repo, remote_uri, monitor)
      
      // Default to this branch.
      String branch_name = GitUtils.tryGettingBranch (git, branch)
      String tag_name = GitUtils.tryGettingTag(git, tag)
    
      // Move the extension into the correct openrefine directory.
      log.debug ("Copying downloaded extension into built refine modules directory.")
      FileUtils.copyDirectory(
        ext_folder,
        target_ext_folder
      )
      
      // Attempt the build.
      log.debug ("Attempting to build the extension")
      
      // Now lets add to the properties file.
      def now = System.currentTimeMillis()
      String zip_name = "${branch_name}"
      
      // Create application config entries.
      entries << [
        "extension.build.date": "${now}",
        "extension.build.branch": "${branch_name}"
      ]
      if (tag_name) {
        entries['extension.build.tag'] = tag_name
        
        // Matcher
        def matcher = tag_name =~ tag_matcher_pattern
        
        if (matcher && matcher?.getAt(0)?.getAt(1)) {
          zip_name = "gokb-release-${matcher[0][1]}"
        }
      } else {
        // Use the timestamp.
        zip_name += "-${now}"
      }
      
      zip_name = zip_name.toLowerCase()
      
      // We can override the build properties here.
      def build_props = ["fullname" : zip_name]
      
      // Build the XML file overriding the properties in the file with our custom values.
      Project p = antBuild(bxml, ant, b_target, build_props)
      
      // Set the location of the built zip file that contains the extension.
      entries['refine_package'] = "${p.getProperty('dist.dir')}/${p.getProperty('fullname')}.zip"
      
      return entries
      
    } finally {
    
      // Release any resources.
      git?.close()
    }
  }

  /**
   * Build ant target from an ant xml duild file.
   */
  public static Project antBuild (File bxml, AntBuilder ant,  String target = null, def props = null) {
    Project p = null
    if (!bxml || !bxml.exists()) {
      log.debug ("build file not found!")
      return p
    }
      
    if (!ant) {
      log.debug ("No ant builder supplied.")
      return p
    }

    log.debug ("Creating ANT project using file ${bxml}.")

    // Create a project in ANT
    p = ant.createProject()
    p.init()
    p.baseDir = bxml.getParentFile()

    // Execute default target.
    log.debug ("Using ${p.baseDir} as base directory")

    props?.each { name, val ->
      p.setProperty("${name}", "${val}")
    }

    // Configure the project using the build file.
    ProjectHelper.projectHelper.parse(p, bxml)
    
    target = target ?: p.defaultTarget
    log.debug ("Executing target ${target}")

    // Execute Default target.
    p.executeTarget(target ?: p.defaultTarget)
    
    return p
  }
  
  /**
   * Uses ant to copy a zip file to another directory with the option of clearing the directory first.
   * 
   * @param zip
   * @param ant
   * @param target_dir
   * @return
   */
  public static void copyZip (AntBuilder ant, String zip, String target_dir, boolean clear_target = true) {
    
    // Make the directory if necessary.
    ant.mkdir (dir:"${target_dir}")
    
    // Clear the directory of any existant files.
    if (clear_target) {
      log.debug ("Clearing ${target_dir} of other zips")
      
      ant.delete (includeEmptyDirs:"false") {
        fileset (dir:"${target_dir}") {
          include (name:"*.zip")
        }
      }
    }
    
    ant.copy (todir:"${target_dir}", overwrite:true, file:"${zip}")
    log.debug ("Copied zip found at ${zip} to ${target_dir}")
  }
}