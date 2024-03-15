#!/bin/bash

# Wait for Blaze to finish initializing
sleep 60

# Remove old output files (if any)
rm -rf /app/test/*

# Run transfair if there is no lock and if there is data
if [ ! -f "/app/data/lock" ] && [ ! -z "$(ls -A /app/data)" ]; then
    cd /app
    java -jar transFAIR.jar
    touch /app/data/lock
fi

# Run forever
tail -f /dev/null

