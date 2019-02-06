#!/bin/bash
if [ ! -f ./google-java-format.jar ]; then
  curl --fail --silent --show-error --location --output google-java-format.jar https://github.com/google/google-java-format/releases/download/google-java-format-1.7/google-java-format-1.7-all-deps.jar
fi

find src -name '*.java' -exec java -jar google-java-format.jar --replace '{}' '+'

