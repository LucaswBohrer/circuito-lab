#!/bin/sh
# Compila tudo e corre os testes do solver.
set -e
cd "$(dirname "$0")"
./build.sh

if [ -x "$HOME/workspace/tools/jdk-21.0.13.1+2/bin/java" ]; then
  JAVA_HOME="$HOME/workspace/tools/jdk-21.0.13.1+2"
  PATH="$JAVA_HOME/bin:$PATH"
  export JAVA_HOME PATH
fi

java -cp out:out-test com.circuitolab.TestRunner
