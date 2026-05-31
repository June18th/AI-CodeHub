#!/bin/sh
# Runs tests and outputs to stdout (visible in docker logs)
cd /app
echo "=== Running tests at $(date) ==="
mvn test -q 2>&1
echo "=== Done ==="
