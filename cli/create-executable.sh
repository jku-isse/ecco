#!/usr/bin/env bash

../gradlew fatJar

cat \
  "stub.sh" \
  "build/libs/ecco-cli-0.1.9-all.jar" > \
  "build/libs/ecco"

chmod +x "build/libs/ecco"
