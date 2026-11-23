#!/bin/sh
# Compila o CircuitoLab (main + testes) só com javac. Sem Maven/Gradle.
set -e
cd "$(dirname "$0")"

if [ -x "$HOME/workspace/tools/jdk-21.0.13.1+2/bin/javac" ]; then
  JAVA_HOME="$HOME/workspace/tools/jdk-21.0.13.1+2"
  PATH="$JAVA_HOME/bin:$PATH"
  export JAVA_HOME PATH
fi

mkdir -p out out-test
find src/main/java -name '*.java' > /tmp/circuitolab-main-sources.txt
javac -d out @/tmp/circuitolab-main-sources.txt
find src/test/java -name '*.java' > /tmp/circuitolab-test-sources.txt
javac -cp out -d out-test @/tmp/circuitolab-test-sources.txt
echo "Compilação OK: out/ (app) e out-test/ (testes)."
