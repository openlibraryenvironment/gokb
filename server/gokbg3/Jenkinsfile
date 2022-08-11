node {
  jdk = tool name: 'OracleJava8'
  env.JAVA_HOME = "${jdk}"

  stage('Test') {
    checkout scm
    dir ('server/gokbg3') {
      sh './gradlew --no-daemon --console plain test'
    }
  }

  stage('Build') {
    dir ('server/gokbg3') {
//      def props = readProperties file: 'gradle.properties'
      sh './gradlew --no-daemon --console plain war'
    }
  }

  stage('Deploy') {
     dir ('server/gokbg3') {
        def props = readProperties file: 'gradle.properties'
//        sh "echo ${props.appVersion}"
        sh "cp build/libs/gokbg3-${props.appVersion}.war /opt/laser/apache-tomcat/default/webapps/gokb.${props.appVersion}#${BUILD_NUMBER}.${env.BRANCH_NAME}.war.bak"
        sh "cp build/libs/gokbg3-${props.appVersion}.war /opt/laser/apache-tomcat/default/webapps/gokb.war"
     }
  }
}