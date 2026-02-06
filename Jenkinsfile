pipeline {
    agent any

    tools {
        jdk 'JDK21'
        gradle 'Gradle-latest'
    }

    environment {
        TOMCAT_HOME = '/opt/tomcat'
        APP_NAME = 'wherehouse'
        WAR_FILE = 'wherehouse.war'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        retry(1)
        timestamps()
    }

    stages {
        stage('Git Clone') {
            steps {
                echo "=== Git Clone ==="
                cleanWs()
                git branch: 'master',
                    url: 'https://github.com/bumjinDev/wherehouse_SpringBoot.git',
                    credentialsId: 'github-credentials'
            }
        }

        stage('Setup Build Environment') {
            steps {
                dir('wherehouse') {
                    sh 'chmod +x ./gradlew'
                    sh './gradlew --version'
                }
            }
        }

        stage('Compile Test') {
            steps {
                dir('wherehouse') {
                    sh './gradlew compileJava'
                }
            }
        }

        stage('Build WAR') {
            steps {
                dir('wherehouse') {
                    sh './gradlew clean bootWar'
                    sh 'ls -lah build/libs/wherehouse.war'
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    echo "=== Deploy ==="
                    def TIMESTAMP = new Date().format('yyyyMMdd_HHmmss')

                    sh 'sudo /opt/tomcat/bin/shutdown.sh || true'
                    sh 'sleep 5'
                    sh 'sudo pkill -9 -f tomcat || true'
                    sh 'sudo pkill -9 -f catalina || true'
                    sh 'sleep 3'

                    sh 'sudo cp wherehouse/build/libs/wherehouse.war /opt/tomcat/webapps/'
                    sh 'sudo chown tomcat:tomcat /opt/tomcat/webapps/wherehouse.war'
                    sh 'sudo chmod 755 /opt/tomcat/webapps/wherehouse.war'

                    sh 'sudo -u tomcat /opt/tomcat/bin/startup.sh'
                    echo "=== Deploy Done ==="
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    sleep(30)
                    sh 'sudo netstat -tlnp | grep :8080 || echo port not ready'
                }
            }
        }
    }

    post {
        success {
            echo 'Build and Deploy SUCCESS'
        }
        failure {
            echo 'Build or Deploy FAILED'
        }
    }
}
