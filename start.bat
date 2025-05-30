@echo off
echo 启动 Easy Sharer...
echo.

REM 编译项目
echo 正在编译项目...
mvn compile -q

if %ERRORLEVEL% neq 0 (
    echo 编译失败，请检查代码错误
    pause
    exit /b 1
)

echo 编译成功，正在启动应用...
echo.

REM 启动应用
mvn spring-boot:run

pause 