eventCompileStart = { kind ->
  def buildNumber = metadata.'app.buildNumber'

  if (!buildNumber)
    buildNumber = 1
  else
    buildNumber = Integer.valueOf(buildNumber) + 1

  def getBranchCommand = """git rev-parse --abbrev-ref HEAD"""
  def commit = new ByteArrayOutputStream()
  def branch = new ByteArrayOutputStream()

  exec {
    commandLine 'git', 'rev-parse', 'HEAD'
    standardOutput = commit
  }

  metadata.'repository'.'revision'.'number' = commit.toString()

  exec {
    commandLine 'git', 'rev-parse', '--abbrev-ref', 'HEAD'
    standardOutput = branch
  }

  metadata.'repository'.'branch' = branch.toString()

  def formatter = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
  def buildDate = formatter.format(new Date(System.currentTimeMillis()))
  metadata.'app.buildDate' = buildDate
  metadata.'app.buildProfile' = grailsEnv

  metadata.'app.buildNumber' = buildNumber.toString()

  metadata.persist()

  println "**** Compile Starting on Build #${buildNumber}"
}

eventCreateWarStart = { warName, stagingDir ->

  println "\n[Start add additional properties for war file]\n"
  def buildDateTimeStamp = LocalDateTime.now()

  def RevisionNumber = ant.antProject.properties."environment.GIT_COMMIT"?: ant.antProject.properties."environment.SVN_REVISION"

  if (!RevisionNumber) {
    try {
      def command = """git rev-parse HEAD"""
      def exec = command.execute()
      exec.waitFor()
      if (exec.exitValue() == 0) {
        RevisionNumber = exec.in.text
      }
    } catch (IOException e) {
    }
  }
  if (!RevisionNumber) {
    File entries = new File(basedir, '.svn/entries')
    if (entries.exists() && entries.text.split('\n').length>3) {
      evisionNumber = entries.text.split('\n')[3].trim()
    }
  }

  RevisionNumber = RevisionNumber?: 'UNKNOWN'

  def CheckedOutBranch = ant.antProject.properties."environment.GIT_BRANCH"?:ant.antProject.properties."environment.SVN_URL"

  if (!CheckedOutBranch) {
    try {
      def command = """git rev-parse --abbrev-ref HEAD"""
      def exec = command.execute()
      exec.waitFor()
      if (exec.exitValue() == 0) {
        CheckedOutBranch = exec.in.text
      }
    } catch (IOException e) {
    }
  }

  CheckedOutBranch = CheckedOutBranch?: 'UNKNOWN'

  ant.propertyfile(file: "${stagingDir}/WEB-INF/classes/gradle.properties") {
    entry(key:"build.DateTimeStamp", value: buildDateTimeStamp)
    entry(key:"repository.revision.number", value: RevisionNumber )
    entry(key:"repository.branch", value: CheckedOutBranch)
  }


  println "\n[End add additional properties for war file:\n DateTimeStamp:${buildDateTimeStamp}, RevisionNumber: ${RevisionNumber}, Checkedout Branch:${CheckedOutBranch}]\n"

}
