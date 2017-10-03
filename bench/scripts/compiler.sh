#!/usr/bin/env bash
find -L compiler/target/scala-2.12/src_managed/ compiler/src/ -type f \( -name "*.scala" -or -name "*.java" \) -exec echo "dotty-bench-bootstrapped/jmh:run 5 10" {} + | sbt
