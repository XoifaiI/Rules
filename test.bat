@echo off
REM Compile and run tests
REM Usage: test.bat [package]
REM   test.bat              = Run all tests
REM   test.bat rules        = Run all rules tests
REM   test.bat re2j         = Run all re2j tests

if not exist bin mkdir bin

echo Compiling core library and testing framework...
javac -cp "lib/*" -d bin src/main/java/re2j/*.java src/main/java/rules/*.java src/main/java/testing/*.java
if %ERRORLEVEL% NEQ 0 (
    echo Core library compilation failed
    exit /b 1
)

echo Compiling all tests...
javac -cp "bin;lib/*" -d bin src/test/java/rules/*.java src/test/java/re2j/*.java
if %ERRORLEVEL% NEQ 0 (
    echo Some tests failed to compile.
)

if "%~1"=="" goto RunAll
if /I "%~1"=="rules" goto RunRules
if /I "%~1"=="re2j" goto RunRE2J
goto RunAll

:RunAll
echo.
echo Running all tests...
echo.
for %%f in (bin\rules\*Test.class bin\rules\*Tests.class) do (
    set "name=%%~nf"
    setlocal enabledelayedexpansion
    echo Running rules.!name!...
    java -cp "bin;lib/*;src/test/resources" rules.!name!
    endlocal
)
for %%f in (bin\re2j\*Test.class) do (
    set "name=%%~nf"
    setlocal enabledelayedexpansion
    echo Running re2j.!name!...
    java -cp "bin;lib/*;src/test/resources" re2j.!name!
    endlocal
)
goto End

:RunRules
echo.
echo Running rules tests...
echo.
for %%f in (bin\rules\*Test.class bin\rules\*Tests.class) do (
    set "name=%%~nf"
    setlocal enabledelayedexpansion
    echo Running rules.!name!...
    java -cp "bin;lib/*;src/test/resources" rules.!name!
    endlocal
)
goto End

:RunRE2J
echo.
echo Running re2j tests...
echo.
for %%f in (bin\re2j\*Test.class) do (
    set "name=%%~nf"
    setlocal enabledelayedexpansion
    echo Running re2j.!name!...
    java -cp "bin;lib/*;src/test/resources" re2j.!name!
    endlocal
)
goto End

:End
echo.
echo Tests complete.
