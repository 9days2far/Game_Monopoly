@echo off
chcp 65001 >nul
pushd "%~dp0"
if not exist out mkdir out
echo [1/2] Dang bien dich Java 21 Server + Core OOP...
javac -encoding UTF-8 -d out board/*.java card/*.java dice/*.java player/*.java square/*.java trade/*.java server/*.java
if errorlevel 1 (
    echo [LOI] Bien dich that bai. Vui long kiem tra da cai dat JDK 21+ hay chua.
    pause
    popd
    exit /b 1
)
echo [2/2] Dang khoi dong Web Server tai cong 8080...
java -cp out ProjectOop.web.WebServerMain
popd
