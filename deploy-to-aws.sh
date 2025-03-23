#!/bin/bash

# EasyBank AWS Deployment Script
# This script automates the deployment of EasyBank to AWS EKS

# Exit on any error
set -e

# Configuration - Replace these values with your own
AWS_ACCOUNT_ID="217281135028"
AWS_REGION="us-west-2"  # Change to your preferred region
EKS_CLUSTER_NAME="easybank-cluster"
ECR_REPOSITORY="easybank-app"

# Set proxy settings
export https_proxy=http://127.0.0.1:8118
export http_proxy=http://127.0.0.1:8118
export HTTP_PROXY=http://127.0.0.1:8118
export HTTPS_PROXY=http://127.0.0.1:8118
export AWS_CLI_FILE_ENCODING=UTF-8
# Disable SSL verification for development
export AWS_CA_BUNDLE=""
export PYTHONWARNINGS="ignore:Unverified HTTPS request"

# Configure Docker to use proxy
mkdir -p ~/.docker
cat > ~/.docker/config.json <<EOF
{
  "proxies": {
    "default": {
      "httpProxy": "http://127.0.0.1:8118",
      "httpsProxy": "http://127.0.0.1:8118",
      "noProxy": "localhost,127.0.0.1,*.amazonaws.com"
    }
  }
}
EOF

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== EasyBank AWS Deployment ===${NC}"

# Test AWS CLI connectivity
echo -e "${GREEN}Testing AWS CLI connectivity...${NC}"
if ! aws sts get-caller-identity; then
    echo "Failed to connect to AWS. Please check your proxy settings and AWS credentials."
    exit 1
fi

# Check AWS CLI and kubectl installation
if ! command -v aws &> /dev/null; then
    echo "AWS CLI is not installed. Please install it first."
    exit 1
fi

if ! command -v kubectl &> /dev/null; then
    echo "kubectl is not installed. Please install it first."
    exit 1
fi

# 1. Update kubectl context to point to your EKS cluster
echo -e "${GREEN}Updating kubectl context to point to EKS cluster...${NC}"
aws eks update-kubeconfig --name $EKS_CLUSTER_NAME --region $AWS_REGION

# 2. Build and push Docker image to ECR
echo -e "${GREEN}Building and pushing Docker image to ECR...${NC}"
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Check if repository exists, if not create it
if ! aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION &> /dev/null; then
    echo "Creating ECR repository..."
    aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION
fi

# Build and tag the Docker image
echo "Building Docker image..."
docker build -t $ECR_REPOSITORY:latest .
echo "Tagging Docker image..."
docker tag $ECR_REPOSITORY:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:latest
echo "Pushing Docker image to ECR..."
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:latest

# 3. Update Kubernetes configuration files with actual values
echo -e "${GREEN}Updating Kubernetes configuration files...${NC}"

# Replace placeholders in app-deployment.yaml with actual ECR repository
sed -i.bak "s|\${AWS_ACCOUNT_ID}|$AWS_ACCOUNT_ID|g" k8s/app-deployment.yaml
sed -i.bak "s|\${AWS_REGION}|$AWS_REGION|g" k8s/app-deployment.yaml

# 4. Check if AWS endpoints are properly configured
if grep -q "\${DB_ENDPOINT}" k8s/config.yaml || grep -q "\${REDIS_ENDPOINT}" k8s/config.yaml; then
    echo -e "${YELLOW}WARNING: AWS service endpoints not configured in k8s/config.yaml${NC}"
    echo "Please run ./setup-aws-services.sh first to create and configure AWS RDS and ElastiCache"
    exit 1
fi

# 5. Apply Kubernetes resources
echo -e "${GREEN}Applying Kubernetes resources...${NC}"
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/db-migration-sql.yaml
kubectl apply -f k8s/app-deployment.yaml
kubectl apply -f k8s/app-service.yaml

# Run DB migration job to set up the database schema
echo -e "${GREEN}Running database migration job...${NC}"
kubectl apply -f k8s/migration-job.yaml
kubectl wait --for=condition=complete --timeout=300s job/db-migration

# 6. Wait for the deployment to complete
echo -e "${GREEN}Waiting for deployment to complete...${NC}"
kubectl rollout status deployment/easybank-app

# 7. Get external access details
echo -e "${GREEN}Getting access details...${NC}"
echo "Waiting for load balancer to be provisioned..."
sleep 30
ENDPOINT=$(kubectl get service easybank-service -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

echo -e "${YELLOW}=== Deployment Complete ===${NC}"
echo "EasyBank is now deployed to EKS!"
echo "You can access the application at: http://$ENDPOINT"
echo "To monitor the deployment, use: kubectl get pods"
echo "To view logs, use: kubectl logs deployment/easybank-app" 