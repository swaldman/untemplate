#!/bin/bash

. scala-cli-deps.sh

scala-cli package ${SCALA_CLI_DEPS} --graal -o untemplate ./src/main/scala

