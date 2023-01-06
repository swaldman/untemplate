#!/bin/bash

. scala-cli-deps.sh

scala-cli package ${SCALA_CLI_DEPS} -o untemplate ./src/main/scala


