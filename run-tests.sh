#!/usr/bin/env bash
# spine38 指纹验证（JVM 直跑，绕开 Gradle 测试任务的 argfile 编码坑）
# 背景：项目路径含中文「芋圆」，Gradle 守护进程(UTF-8)写 @argfile、JVM 启动器用系统
# 原生码页(GBK)解码 → 非 ASCII classpath 被解坏 → ClassNotFoundException。
# 命令行直传 classpath 走 CreateProcess(UTF-16) 无此问题。
set -e
P="D:/Angelina-pet-芋圆-安卓版"
JAVA="D:/devecostudio-windows-6.1.1.300/DevEco Studio/jbr/bin/java.exe"
G="C:/Users/pc/.gradle/caches/modules-2/files-2.1"
CP="$P/app/build/tmp/kotlin-classes/debugUnitTest;$P/app/build/tmp/kotlin-classes/debug;$P/app/src/test/resources"
CP="$CP;$G/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar"
CP="$CP;$G/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar"
CP="$CP;$G/org.jetbrains.kotlin/kotlin-stdlib/2.1.0/85f8b81009cda5890e54ba67d64b5e599c645020/kotlin-stdlib-2.1.0.jar"
cd "$P"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$CP" org.junit.runner.JUnitCore \
  com.jngkzbird.arknights_angelina_pet.spine38.Spine38FingerprintTest
