#!/bin/sh

source $(dirname $0)/setenv.sh

java -classpath $CLASSPATH1 xj.mobile.tool.IOSDocReader $* 

exit 0