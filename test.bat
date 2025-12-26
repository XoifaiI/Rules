@echo off
REM Compile and run tests
REM Usage: test.bat [TestClass]
REM   test.bat              - Run SimpleTest (working tests)
REM   test.bat Simple       - Run SimpleTest
REM   test.bat String       - Run StringRulesExploitTest
REM   test.bat MemoryLeak   - Run MemoryLeakTests
REM   test.bat all          - Attempt to run all exploit tests

if not exist bin mkdir bin

echo Compiling core library and testing framework...
javac -cp "lib/*" -d bin src/main/java/rules/*.java src/main/java/testing/*.java
if %ERRORLEVEL% NEQ 0 (
    echo Core library compilation failed
    exit /b 1
)

if "%~1"=="" goto RunSimple
if /I "%~1"=="all" goto RunAll
if /I "%~1"=="Simple" goto RunSimple
if /I "%~1"=="MemoryLeak" goto RunMemoryLeak
goto RunSpecific

:RunSimple
echo Compiling SimpleTest...
javac -cp "bin;lib/*" -d bin src/test/java/rules/SimpleTest.java
if %ERRORLEVEL% NEQ 0 (
    echo SimpleTest compilation failed
    exit /b 1
)
echo.
echo Running SimpleTest...
echo.
java -cp "bin;lib/*" rules.SimpleTest
goto End

:RunAll
echo Compiling all tests...
javac -cp "bin;lib/*" -d bin src/test/java/rules/*.java
if %ERRORLEVEL% NEQ 0 (
    echo Some tests failed to compile. Running AllExploitTests anyway...
)
echo.
java -cp "bin;lib/*" rules.AllExploitTests
goto End

:RunMemoryLeak
echo Compiling MemoryLeakTests...
javac -cp "bin;lib/*" -d bin src/test/java/rules/MemoryLeakTests.java
if %ERRORLEVEL% NEQ 0 (
    echo MemoryLeakTests compilation failed
    exit /b 1
)
echo.
echo Running MemoryLeakTests - chaos engineering memory leak detection...
echo.
java -cp "bin;lib/*" rules.MemoryLeakTests
goto End

:RunSpecific
echo Compiling %~1RulesExploitTest...
javac -cp "bin;lib/*" -d bin src/test/java/rules/%~1RulesExploitTest.java 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed for %~1RulesExploitTest
    exit /b 1
)
echo.
echo Running %~1RulesExploitTest...
echo.
java -cp "bin;lib/*" rules.%~1RulesExploitTest
goto End

:End