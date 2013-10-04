package org.gokb

import org.gokb.cred.*

class PackageService {

  /**
   * Lookup or create a package based on the supplied package name.
   * Incremental will edit an existing package. If it's false the package and it's
   * TIPPs will have their status set to retired as well and a new package returned.
   */
  def findCorrectPackage (Map<String, Boolean> retired_package, String package_name, boolean incremental) {

	log.debug("Trying to find a package for ${!incremental ? 'none-incremental' : 'incremental'} update using ${package_name}.")

	// Package.
	Package pkg = findCurrentPackage (package_name)

	// If we don't have a package then we need to create one and the incremental flag
	// becomes irrelevant.
	if (!pkg) {

	  log.debug("No current package found. Just create a new one.")

	  // Just create a new package.
	  pkg = new Package(name:package_name)
	  
	  // Ensure we add and create a new ID
	  pkg.getIds().add(
		Identifier.lookupOrCreateCanonicalIdentifier('gokb-pkgid', package_name)
	  )

	  log.debug("Created package with id ${pkg.id}")

	} else {

	  // If this is a new package then we should retire the current one.
	  if (!incremental) {
		
		if (!retired_package.get(package_name)) {

		  // Retire each TIPP
		  pkg.getTipps().each { def tipp ->

			// Retire
			tipp.retire()
			log.debug("TIPP ${tipp.id} retired.")
		  }

		  // Then retire the package.
		  pkg.retire()

		  // Create a new package with the IDs
		  Set<Identifier> pkIds = pkg.ids.findAll { Identifier the_id ->
			the_id?.getNamespace()?.getValue()?.equalsIgnoreCase('gokb-pkgid')
		  }

		  // Save the old one.
		  if ( pkg.save(failOnError:true) ) {
			log.debug ("Retired and saved package ${pkg.id}.")
		  }

		  // New package.
		  pkg = new Package(name:package_name)

		  // Add all the ids.
		  pkg.ids.addAll(pkIds)
		  
		  // Add to the map.
		  retired_package.put(package_name, true)
		}
	  }
	}
	
	// Save the Package.
	pkg.save(failOnError:true)
	
	pkg
  }

  Package findCurrentPackage (String package_name) {

	// Try and find a package
	def q = ComboCriteria.createFor(Package.createCriteria())
	def existingPkg = q.get {
	  and {
		q.add ("ids.namespace.value", "ilike", 'gokb-pkgid')
		q.add ("ids.value", "ilike", "${package_name}")

		// Also needs to be an active package.
		q.add ("status.owner.desc", "ilike", KBComponent.RD_STATUS)
		q.add ("status.value", "ilike", KBComponent.STATUS_CURRENT)
	  }
	}

	existingPkg
  }
}
