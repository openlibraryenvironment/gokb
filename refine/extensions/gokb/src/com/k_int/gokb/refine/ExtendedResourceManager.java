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
    final static Logger logger = LoggerFactory.getLogger("GOKb Extended resource manager.");

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

    static public void removePath (
            String bundleName,
            ButterflyModule module,
            String path) {

        // Get the bundle and create if not there.
        ExtendedResourceBundle bundle = getBundle(bundleName);

        // Get the full path.
        String fullPath = resolve(module, path);
        if (fullPath == null) {
            logger.error("Failed to remove paths for unmounted module " + module.getName());
        }
        if (bundle.getPathSet().contains(fullPath)) {

            // Need to look for the matching QualifiedPath
            ListIterator<QualifiedPath> qPaths = bundle.getPathList().listIterator();

            boolean done = false;

            while (!done && qPaths.hasNext()) {
                QualifiedPath qp = qPaths.next();
                if (fullPath.equals(qp.fullPath)) {

                    // This is the path we wish to remove, so do it.
                    qPaths.remove();
                    bundle.getPathSet().remove(fullPath);
                    logger.debug("Removed entry for " + fullPath);
                    done = true;
                }
            }
        }
    }
}