#!/bin/bash

# Script to set up AWS RDS and ElastiCache for EasyBank
# Exit on any error
set -e

# Set proxy settings
export https_proxy=http://127.0.0.1:8118
export http_proxy=http://127.0.0.1:8118
export HTTP_PROXY=http://127.0.0.1:8118
export HTTPS_PROXY=http://127.0.0.1:8118
export AWS_CLI_FILE_ENCODING=UTF-8
# Disable SSL verification for development
export AWS_CA_BUNDLE=""
export PYTHONWARNINGS="ignore:Unverified HTTPS request"

# Configuration
AWS_REGION="us-west-2"
DB_INSTANCE_IDENTIFIER="easybank-db"
DB_NAME="easybank"
DB_USERNAME="postgres"
DB_PASSWORD="postgres123" # You should use a more secure password in production
DB_INSTANCE_CLASS="db.t3.micro" # Smallest RDS instance (free tier eligible)

CACHE_CLUSTER_ID="easybank-redis"
CACHE_NODE_TYPE="cache.t3.micro" # Smallest ElastiCache node type
CACHE_ENGINE="redis"
CACHE_ENGINE_VERSION="7.0"

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Setting up AWS Services for EasyBank ===${NC}"

# Test AWS CLI connectivity
echo -e "${GREEN}Testing AWS CLI connectivity...${NC}"
if ! aws sts get-caller-identity; then
    echo "Failed to connect to AWS. Please check your proxy settings and AWS credentials."
    exit 1
fi

# Get default VPC ID
echo -e "${GREEN}Getting default VPC information...${NC}"
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query "Vpcs[0].VpcId" --output text)
if [ -z "$VPC_ID" ]; then
    echo "No default VPC found. Please create a VPC first."
    exit 1
fi

# Get subnet IDs from the default VPC
SUBNET_IDS=$(aws ec2 describe-subnets \
    --filters "Name=vpc-id,Values=$VPC_ID" \
    --query "Subnets[*].SubnetId" \
    --output text)

# Get or create security group for RDS
echo -e "${GREEN}Setting up security group for RDS...${NC}"
RDS_SG_NAME="easybank-rds-sg"
RDS_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$RDS_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --query "SecurityGroups[0].GroupId" \
    --output text 2>/dev/null || echo "")

if [ -z "$RDS_SG_ID" ] || [ "$RDS_SG_ID" == "None" ]; then
    echo "Creating new security group for RDS..."
    RDS_SG_ID=$(aws ec2 create-security-group \
        --group-name $RDS_SG_NAME \
        --description "Security group for EasyBank RDS" \
        --vpc-id $VPC_ID \
        --query "GroupId" \
        --output text)

    # Allow PostgreSQL traffic from anywhere (for development - restrict in production)
    aws ec2 authorize-security-group-ingress \
        --group-id $RDS_SG_ID \
        --protocol tcp \
        --port 5432 \
        --cidr 0.0.0.0/0
else
    echo "Using existing RDS security group: $RDS_SG_ID"
fi

# Get or create security group for ElastiCache
echo -e "${GREEN}Setting up security group for ElastiCache...${NC}"
CACHE_SG_NAME="easybank-redis-sg"
CACHE_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$CACHE_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --query "SecurityGroups[0].GroupId" \
    --output text 2>/dev/null || echo "")

if [ -z "$CACHE_SG_ID" ] || [ "$CACHE_SG_ID" == "None" ]; then
    echo "Creating new security group for ElastiCache..."
    CACHE_SG_ID=$(aws ec2 create-security-group \
        --group-name $CACHE_SG_NAME \
        --description "Security group for EasyBank ElastiCache" \
        --vpc-id $VPC_ID \
        --query "GroupId" \
        --output text)

    # Allow Redis traffic from anywhere (for development - restrict in production)
    aws ec2 authorize-security-group-ingress \
        --group-id $CACHE_SG_ID \
        --protocol tcp \
        --port 6379 \
        --cidr 0.0.0.0/0
else
    echo "Using existing ElastiCache security group: $CACHE_SG_ID"
fi

# Create or update DB subnet group
echo -e "${GREEN}Setting up DB subnet group...${NC}"
if ! aws rds describe-db-subnet-groups --db-subnet-group-name easybank-db-subnet >/dev/null 2>&1; then
    echo "Creating new DB subnet group..."
    aws rds create-db-subnet-group \
        --db-subnet-group-name easybank-db-subnet \
        --db-subnet-group-description "Subnet group for EasyBank RDS" \
        --subnet-ids $SUBNET_IDS
else
    echo "DB subnet group already exists"
fi

# Check if RDS instance exists
echo -e "${GREEN}Checking RDS instance...${NC}"
if ! aws rds describe-db-instances --db-instance-identifier $DB_INSTANCE_IDENTIFIER >/dev/null 2>&1; then
    echo "Creating new RDS PostgreSQL instance..."
    # Create RDS PostgreSQL instance
    aws rds create-db-instance \
        --db-instance-identifier $DB_INSTANCE_IDENTIFIER \
        --db-instance-class $DB_INSTANCE_CLASS \
        --engine postgres \
        --engine-version 14.17 \
        --allocated-storage 20 \
        --master-username $DB_USERNAME \
        --master-user-password $DB_PASSWORD \
        --db-name $DB_NAME \
        --vpc-security-group-ids $RDS_SG_ID \
        --db-subnet-group-name easybank-db-subnet \
        --publicly-accessible \
        --region $AWS_REGION

    # Wait for RDS instance to be available
    echo -e "${GREEN}Waiting for RDS instance to be available (this may take several minutes)...${NC}"
    aws rds wait db-instance-available --db-instance-identifier $DB_INSTANCE_IDENTIFIER --region $AWS_REGION
else
    echo "RDS instance already exists"
fi

# Get RDS endpoint
DB_ENDPOINT=$(aws rds describe-db-instances \
    --db-instance-identifier $DB_INSTANCE_IDENTIFIER \
    --region $AWS_REGION \
    --query "DBInstances[0].Endpoint.Address" \
    --output text)
echo "RDS Endpoint: $DB_ENDPOINT"
# RDS Endpoint: easybank-db.cd2ma6ye0kiw.us-west-2.rds.amazonaws.com

# Create or update cache subnet group
echo -e "${GREEN}Setting up cache subnet group...${NC}"
if ! aws elasticache describe-cache-subnet-groups --cache-subnet-group-name easybank-cache-subnet >/dev/null 2>&1; then
    echo "Creating new cache subnet group..."
    aws elasticache create-cache-subnet-group \
        --cache-subnet-group-name easybank-cache-subnet \
        --cache-subnet-group-description "Subnet group for EasyBank ElastiCache" \
        --subnet-ids $SUBNET_IDS
else
    echo "Cache subnet group already exists"
fi

# Check if ElastiCache cluster exists
echo -e "${GREEN}Checking ElastiCache cluster...${NC}"
if ! aws elasticache describe-cache-clusters --cache-cluster-id $CACHE_CLUSTER_ID >/dev/null 2>&1; then
    echo "Creating new ElastiCache Redis cluster..."
    # Create ElastiCache Redis cluster
    aws elasticache create-cache-cluster \
        --cache-cluster-id $CACHE_CLUSTER_ID \
        --engine $CACHE_ENGINE \
        --engine-version $CACHE_ENGINE_VERSION \
        --cache-node-type $CACHE_NODE_TYPE \
        --num-cache-nodes 1 \
        --cache-subnet-group-name easybank-cache-subnet \
        --security-group-ids $CACHE_SG_ID \
        --region $AWS_REGION

    # Wait for ElastiCache cluster to be available
    echo -e "${GREEN}Waiting for ElastiCache cluster to be available (this may take several minutes)...${NC}"
    aws elasticache wait cache-cluster-available --cache-cluster-id $CACHE_CLUSTER_ID --region $AWS_REGION
else
    echo "ElastiCache cluster already exists"
fi

# Get ElastiCache endpoint
REDIS_ENDPOINT=$(aws elasticache describe-cache-clusters \
    --cache-cluster-id "$CACHE_CLUSTER_ID" \
    --region "$AWS_REGION" \
    --show-cache-node-info \
    --query "CacheClusters[0].CacheNodes[0].Endpoint.Address" \
    --output text)
echo "Redis Endpoint: $REDIS_ENDPOINT"
# Redis Endpoint: easybank-redis.r1nket.0001.usw2.cache.amazonaws.com

# Update k8s/config.yaml with actual endpoint values
echo -e "${GREEN}Updating Kubernetes configuration with endpoints...${NC}"
sed -i.bak "s|\${DB_ENDPOINT}|${DB_ENDPOINT}|g" k8s/config.yaml
sed -i.bak "s|\${REDIS_ENDPOINT}|${REDIS_ENDPOINT}|g" k8s/config.yaml

# Update Kubernetes secrets with new password
echo -e "${GREEN}Updating Kubernetes secrets with new credentials...${NC}"
DB_PASSWORD_BASE64=$(echo -n "${DB_PASSWORD}" | base64)
sed -i.bak "s|cG9zdGdyZXM=|${DB_PASSWORD_BASE64}|g" k8s/secrets.yaml

echo -e "${YELLOW}=== AWS Services Setup Complete ===${NC}"
echo "RDS Endpoint: $DB_ENDPOINT"
echo "Redis Endpoint: $REDIS_ENDPOINT"
echo "Database credentials updated in Kubernetes secrets"
echo "You can now run ./deploy-to-aws.sh to deploy your application" 