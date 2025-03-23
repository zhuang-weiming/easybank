#!/bin/sh
# wait-for-it.sh

set -e

host="$1"
port="$2"
shift 2

echo "Attempting to connect to $host:$port..."

# Try up to 30 times (5 minutes total)
count=0
max_attempts=30

until nc -z -v -w60 "$host" "$port" 2>&1; do
  count=$((count + 1))
  echo "Attempt $count of $max_attempts: Waiting for $host:$port to be ready..."
  
  if [ $count -ge $max_attempts ]; then
    echo "Error: Timed out waiting for $host:$port after $max_attempts attempts"
    exit 1
  fi
  
  sleep 10
done

>&2 echo "$host:$port is up - executing command"

if [ $# -gt 0 ]; then
  exec "$@"
fi 