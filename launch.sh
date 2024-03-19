#!/bin/bash

echo Launch script for transfair

# Wait for Blaze to finish initializing
sleep 60

# Remove old output files (if any)
rm -rf /app/test/*

echo checking prerequisites for transfair

# Run transfair if there is no lock and if there is data
if [ -f /app/data/*.[cC][sS][vV] ] && [ ! -f /app/data/lock ]; then
    cd /app
    echo Run TransFAIR
    java -jar transFAIR.jar
    echo Touching lock file
    touch /app/data/lock
fi

echo -----------------------------------------------------
ls -la /app/data
echo -----------------------------------------------------

echo Loop for ever

# Run forever
tail -f /dev/null

