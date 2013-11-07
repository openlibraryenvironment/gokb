package com.k_int.gokb.refine;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ClientSideResourceManager;

import edu.mit.simile.butterfly.ButterflyModule;


/**
 * Extend the current resource manager to allow for
 * removal of already registered scripts.
 * 
 * The initial purpose of this file is to allow the jQuery
 * libraries to be replaced with updated versions.
 */
public class ExtendedResourceManager extends ClientSideResourceManager {
  final static Logger logger = LoggerFactory.getLogger("GOKb-resources");

  static public class ExtendedResourceBundle extends ClientSideResourceBundle {

    public ExtendedResourceBundle() { /* Defaults */ }
    public ExtendedResourceBundle (String bundleName) {
      // Add all the paths already set...
      for (QualifiedPath path : getPaths(bundleName)) {
        _pathSet.add(path.fullPath);
        _pathList.add(path);
      }
    }

    public Set<String> getPathSet () {
      return _pathSet;
    }

    public List<QualifiedPath> getPathList() {
      return _pathList;
    }
  }

  static public ExtendedResourceBundle getBundle(String bundleName) {

    ClientSideResourceBundle bundle = s_bundles.get(bundleName);
    if (bundle == null) {
      bundle = new ExtendedResourceBundle ();
      s_bundles.put(bundleName, bundle);
    } else {
      if (!(bundle instanceof ExtendedResourceBundle)) {
        // Create our new extended bundle.
        bundle = new ExtendedResourceBundle (bundleName);

        // Replace in the set.
        s_bundles.put(bundleName, bundle);
      }
    }

    return (ExtendedResourceBundle)bundle;
  }

  static public void prependPaths (
      String bundleName,
      ButterflyModule module,
      String[] paths) {
    ExtendedResourceBundle bundle = getBundle(bundleName);
    int count = 0;
    for (String path : paths) {
      String fullPath = resolve(module, path);
      if (fullPath == null) {
        logger.error("Failed to add paths to unmounted module " + module.getName());
        break;
      }
      if (!bundle.getPathSet().contains(fullPath)) {
        QualifiedPath qualifiedPath = new QualifiedPath();
        qualifiedPath.module = module;
        qualifiedPath.path = path;
        qualifiedPath.fullPath = fullPath;

        bundle.getPathSet().add(fullPath);                
        bundle.getPathList().add(count, qualifiedPath);
      }

      count ++;
    }
  }

  static protected String resolveRegex (ButterflyModule module, String path) {
    String prefix, suffix;

    // Check for a slash.
    int pos = path.lastIndexOf("/");

    // We only act if there is a slash.
    if (pos > -1) {
      // Need to split this string.
      prefix = path.substring(0, pos);
      suffix = path.substring(pos + 1);
    } else {
      // Just make relative to root.
      prefix = "";
      suffix = path;
    }

    // The whole thing is to be treated as a regular expression which means,
    // we need to not treat slashes as escape characters.
    prefix = "\\Q" + resolve(module, prefix) + "/\\E";

    // Return the prefix and suffix. 
    return prefix + suffix;
  }

  static public void removePath (
      String bundleName,
      ButterflyModule module,
      String regex) {

    // Just replace with null.
    replacePath (bundleName, module, regex, null, null);
  }

  static public void replacePaths (
      String bundleName,
      ButterflyModule module,
      String[] regexes,
      String new_path,
      ButterflyModule new_mod) {

    // Just run for each resource. We check for duplicates,
    // so only one instance of the replacement will be added.
    for (String regex : regexes) {
      replacePath (bundleName, module, regex, new_path, new_mod);
    }
  }

  static public void replacePath (
      String bundleName,
      ButterflyModule module,
      String regex,
      String new_path,
      ButterflyModule new_mod) {

    // Get the bundle and create if not there.
    ExtendedResourceBundle bundle = getBundle(bundleName);

    // Get the full path.
    String resourceRegex = resolveRegex(module, regex);
    if (resourceRegex == null) {
      logger.error("Failed to remove paths for unmounted module " + module.getName());
    }

    // Need to look for the matching QualifiedPath.
    ListIterator<QualifiedPath> qPaths = bundle.getPathList().listIterator();

    // Flag to terminate while loop early.
    boolean done = false;

    while (!done && qPaths.hasNext()) {
      QualifiedPath qp = qPaths.next();
      if (qp.fullPath.matches(resourceRegex)) {

        // This is the path we wish to remove, so do it.
        qPaths.remove();
        bundle.getPathSet().remove(qp.fullPath);
        logger.info ("Removed " + module.getName() + " resource '" + qp.fullPath + "' as a match for " + resourceRegex);

        // If we have a replacement then swap it out here.
        if (new_path != null) {

          // From module.
          ButterflyModule from_mod = (new_mod != null ? new_mod : module);

          // Create a fully qualified name.
          String fqp = resolve(from_mod, new_path);

          // Check the path hasn't already been added.
          if (!bundle.getPathSet().contains(fqp)) {

            // Create new fully qualified path.
            QualifiedPath new_qp = new QualifiedPath();
            new_qp.module = from_mod;
            new_qp.path = new_path;
            new_qp.fullPath = fqp;

            // Add at current list position.
            qPaths.add(new_qp);

            // Also add to the set.
            bundle.getPathSet().add(fqp);
            logger.info ("\t...replaced with " + from_mod.getName() + " resource '" + fqp + "'");
          }
        }
        done = true;
      }
    }
  }
}