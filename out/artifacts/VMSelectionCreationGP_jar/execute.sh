#!/bin/tcsh 

# The script will first delete the past output
if(-e /local/tmp/tanboxi) then
	rm /local/tmp/tanboxi/ -rf
endif

# $1 is the parameter path
if('$1' == '') then
	echo 'Usage ./execute.sh [param]'
	exit
endif

# create directories to store the output
mkdir /local/tmp/tanboxi/ 
mkdir /local/tmp/tanboxi/VMCreationGP/

# execute the jar file
java -cp VMSelectionCreationGP.jar main.Main $1 &
