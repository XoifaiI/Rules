@echo off
REM Compile all Java files with RE2j dependency to bin directory

if not exist bin mkdir bin

javac -cp "lib/*" -d bin src/main/java/rules/*.java src/main/java/testing/*.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation successful - output in bin/
) else (
    echo Compilation failed
    exit /b 1
)
