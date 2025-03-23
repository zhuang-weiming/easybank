#!/bin/bash

# Check if environment variables are set
if [ -z "$DB_USERNAME" ] || [ -z "$DB_PASSWORD" ]; then
    echo "Error: DB_USERNAME and DB_PASSWORD environment variables must be set"
    exit 1
fi

# Create the secrets.yaml file with base64 encoded values
cat > k8s/secrets.yaml << EOF
apiVersion: v1
kind: Secret
metadata:
  name: easybank-secrets
type: Opaque
data:
  db-username: $(echo -n "$DB_USERNAME" | base64)
  db-password: $(echo -n "$DB_PASSWORD" | base64)
EOF

echo "secrets.yaml has been generated with encoded credentials" 