#!/usr/bin/env bash

mkdir ou1
mkdir out2

dotc -d out1/ tests/link/custom-lib/strawman/collection/Map2.scala

dotc -Xlink-optimise -Ycheck:all -d out2/ -classpath out1/ tests/link/on-custom-lib/map2.scala
