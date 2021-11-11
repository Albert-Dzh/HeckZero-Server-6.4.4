#!/bin/bash
cd ${0%${0##*/}}.

. ./setenv.sh

JAVA="${JAVA_HOME}/bin/java";
APP_HOME=".";
JAR_FILE="$APP_HOME/HeckZero-6.4.4.jar";
LOG_PARAMS="-Dlog4j.configurationFile=conf/log4j2.xml";
NAME="heckzero";
LOCALE_PARAMS="-Duser.language=en -Duser.region=US";
SYSTEM_PARAMS="-Dapp.name=${NAME} -Djava.net.preferIPv4Stack=true ${LOCALE_PARAMS} ${LOG_PARAMS}";

${JAVA} ${SYSTEM_PARAMS} -jar $JAR_FILE
