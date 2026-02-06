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
                echo "=== Git Clone 시작 ==="
                cleanWs()
                git branch: 'master',
                    url: 'https://github.com/bumjinDev/wherehouse_SpringBoot.git',
                    credentialsId: 'github-credentials'
                echo "=== Git Clone 완료 ==="
            }
        }

        stage('Setup Build Environment') {
            steps {
                dir('wherehouse') {
                    sh 'chmod +x ./gradlew'
                    sh './gradlew --version'
                    echo "=== 빌드 환경 설정 완료 ==="
                }
            }
        }

        stage('Compile Test') {
            steps {
                dir('wherehouse') {
                    sh './gradlew compileJava'
                    echo "=== 컴파일 테스트 완료 ==="
                }
            }
        }

        stage('Build WAR') {
            steps {
                dir('wherehouse') {
                    sh './gradlew clean bootWar'
                    sh "ls -lah build/libs/${WAR_FILE}"
                    echo "=== WAR 빌드 완료 ==="
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    echo "=== 배포 시작 ==="
                    def TIMESTAMP = new Date().format('yyyyMMdd_HHmmss')

                    sh """
                        sudo ${TOMCAT_HOME}/bin/shutdown.sh || true
                        sleep 5
                        sudo pkill -9 -f tomcat || true
                        sudo pkill -9 -f catalina || true
                        sleep 3
                    """

                    sh """
                        if [ -d "${TOMCAT_HOME}/webapps/${APP_NAME}" ]; then
                            sudo mv ${TOMCAT_HOME}/webapps/${APP_NAME} ${TOMCAT_HOME}/webapps/${APP_NAME}_backup_${TIMESTAMP}
                        fi
