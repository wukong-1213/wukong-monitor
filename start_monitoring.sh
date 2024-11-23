#!/bin/bash

# JAVA_HOME 설정
export JAVA_HOME="/home/wukong/.jdks/temurin-17.0.13"

# 이전 실행 중인 프로세스 종료
ps -ax | grep 'monitoring.*jar' | awk '{print $1}' | xargs kill 2>/dev/null || true

# Gradle 빌드
./gradlew bootJar

# 빌드 성공 확인
if [ $? -eq 0 ]; then
    # nohup으로 실행
    nohup $JAVA_HOME/bin/java -jar build/libs/monitoring-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
    echo "Application started with PID $!"
else
    echo "Build failed"
    exit 1
fi
