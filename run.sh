#!/bin/sh
# Compila (se preciso) e abre a GUI do CircuitoLab.
set -e
cd "$(dirname "$0")"

if [ -x "$HOME/workspace/tools/jdk-21.0.13.1+2/bin/java" ]; then
  JAVA_HOME="$HOME/workspace/tools/jdk-21.0.13.1+2"
  PATH="$JAVA_HOME/bin:$PATH"
  export JAVA_HOME PATH
fi

[ -d out ] || ./build.sh
java -cp out com.circuitolab.app.Main "$@"
