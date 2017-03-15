eventCompileStart = { kind ->
  def buildNumber = metadata.'app.buildNumber'

  if (!buildNumber)
    buildNumber = 1
  else
    buildNumber = Integer.valueOf(buildNumber) + 1

  def formatter = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
  def buildDate = formatter.format(new Date(System.currentTimeMillis()))
  metadata.'app.buildDate' = buildDate
  metadata.'app.buildProfile' = grailsEnv

  metadata.'app.buildNumber' = buildNumber.toString()

  metadata.persist()

  println "**** Compile Starting on Build #${buildNumber}"
}

// Require our extension build script
// includeTargets << new File("${basedir}/scripts/RefineExtension.groovy")

/**
 * Add our extension to grails.
 */
// eventPackagingEnd = {
//   
//   if ("${argsMap['refine_module']}" != "false") {
//     println ("Building Refine module...")
//   
//     // After the app has been packaged for deployment we need to package the extension, so that
//     // it is included in our .war file.
//     packageExtension()
//   } else {
//     println ("Skipping refine build due to command line arg...")
//   }
// }
