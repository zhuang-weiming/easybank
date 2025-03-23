#!/bin/sh
# wait-for-it.sh

set -e

host="$1"
shift
cmd="$@"

until nc -z -v -w30 "$host" 2>&1 >/dev/null; do
  echo "Waiting for $host to be ready..."
  sleep 1
done

>&2 echo "$host is up - executing command"
exec $cmd 