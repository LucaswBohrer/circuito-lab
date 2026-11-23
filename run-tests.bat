@echo off
REM CircuitoLab — compila e roda os testes no Windows (JDK 21 no PATH).
REM Uso: .\run-tests.bat
setlocal
if not exist out mkdir out
if not exist out-test mkdir out-test
dir /s /b src\main\java\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (
  echo Falha na compilacao (app).
  exit /b 1
)
dir /s /b src\test\java\*.java > test-sources.txt
javac -cp out -d out-test @test-sources.txt
if errorlevel 1 (
  echo Falha na compilacao (testes).
  exit /b 1
)
java -cp "out;out-test" com.circuitolab.TestRunner
endlocal
