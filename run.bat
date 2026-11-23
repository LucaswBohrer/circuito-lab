@echo off
REM CircuitoLab — build + run no Windows (JDK 21 no PATH).
setlocal
if not exist out mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (
  echo Falha na compilacao.
  exit /b 1
)
java -cp out com.circuitolab.app.Main %*
endlocal
