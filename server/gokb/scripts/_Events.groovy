eventCompileStart = { kind ->
  def buildNumber = metadata.'app.buildNumber'

  if (!buildNumber)
    buildNumber = 1
  else
    buildNumber = Integer.valueOf(buildNumber) + 1

  def formatter = new java.text.SimpleDateFormat("MMM dd, yyyy")
  def buildDate = formatter.format(new Date(System.currentTimeMillis()))
  metadata.'app.buildDate' = buildDate
  metadata.'app.buildProfile' = grailsEnv

  metadata.'app.buildNumber' = buildNumber.toString()

  metadata.persist()

  println "**** Compile Starting on Build #${buildNumber}"
}
