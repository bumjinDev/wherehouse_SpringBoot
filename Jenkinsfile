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
        // 1. Git 소스 코드 복제
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

        // 2. 빌드 환경 설정
        stage('Setup Build Environment') {
            steps {
                dir('wherehouse') {
                    sh 'chmod +x ./gradlew'
                    sh './gradlew --version'
                    echo "=== 빌드 환경 설정 완료 ==="
                }
            }
        }

        // 3. 컴파일 테스트
        stage('Compile Test') {
            steps {
                dir('wherehouse') {
                    sh './gradlew compileJava'
                    echo "=== 컴파일 테스트 완료 ==="
                }
            }
        }

        // 4. WAR 파일 빌드
        stage('Build WAR') {
            steps {
                dir('wherehouse') {
                    sh './gradlew clean bootWar'
                    sh "ls -lah build/libs/${WAR_FILE}"
                    echo "=== WAR 빌드 완료 ==="
                }
            }
        }

        // 5. 배포
        stage('Deploy') {
            steps {
                script {
                    echo "=== 배포 시작 ==="

                    def TIMESTAMP = new Date().format('yyyyMMdd_HHmmss')

                    // 5-1. 톰캣 중지
                    sh """
                        ${TOMCAT_HOME}/bin/shutdown.sh || true
                        sleep 5
                        pkill -9 -f tomcat || true
                        pkill -9 -f catalina || true
                        sleep 3
                    """

                    // 5-2. 기존 애플리케이션 백업
                    sh """
                        if [ -d "${TOMCAT_HOME}/webapps/${APP_NAME}" ]; then
                            mv ${TOMCAT_HOME}/webapps/${APP_NAME} ${TOMCAT_HOME}/webapps/${APP_NAME}_backup_${TIMESTAMP}
                        fi
                        if [ -f "${TOMCAT_HOME}/webapps/${WAR_FILE}" ]; then
                            cp ${TOMCAT_HOME}/webapps/${WAR_FILE} ${TOMCAT_HOME}/webapps/${WAR_FILE}.backup_${TIMESTAMP}
                        fi
                    """

                    // 5-3. 새 WAR 파일 복사
                    sh """
                        cp wherehouse/build/libs/${WAR_FILE} ${TOMCAT_HOME}/webapps/
                        chown tomcat:tomcat ${TOMCAT_HOME}/webapps/${WAR_FILE}
                        chmod 755 ${TOMCAT_HOME}/webapps/${WAR_FILE}
                    """

                    // 5-4. 톰캣 시작
                    sh """
                        sudo -u tomcat ${TOMCAT_HOME}/bin/startup.sh
                    """

                    echo "=== 배포 완료 ==="
                }
            }
        }

        // 6. 헬스 체크
        stage('Health Check') {
            steps {
                script {
                    echo "=== 헬스 체크 시작 ==="
                    sleep(30)

                    sh "netstat -tlnp | grep :8080 || echo '포트 8080 아직 대기 중'"
                    sh "ls -la ${TOMCAT_HOME}/webapps/${APP_NAME}/ | head -5 || echo '디렉토리 아직 생성 중'"

                    echo "=== 헬스 체크 완료 ==="
                }
            }
        }
    }

    post {
        always {
            echo "=== 파이프라인 실행 완료 ==="
            script {
                if (fileExists('wherehouse/build/libs/wherehouse.war')) {
                    archiveArtifacts artifacts: 'wherehouse/build/libs/*.war',
                                   fingerprint: true,
                                   allowEmptyArchive: false
                }
            }
        }
        success {
            echo "배포가 성공적으로 완료되었습니다."
        }
        failure {
            echo "배포 중 오류가 발생했습니다. 빌드 로그를 확인하세요."
        }
    }
}

