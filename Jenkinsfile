pipeline {
    agent any
    environment {
        GEMINI_API_KEY = credentials('gemini-api-key')
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'https://github.com/chandandhabale/TalentVector.git',
                    branch: 'main'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
    }
}
