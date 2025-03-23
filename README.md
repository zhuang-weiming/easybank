# Easy Bank - Real-Time Balance Calculation System

A high-performance, resilient banking system built with Spring Boot and deployed on Kubernetes with AWS infrastructure.

## Architecture Overview

The system is designed with the following components:

- **Spring Boot Backend**:
  - RESTful API for account management and transaction processing
  - Transaction processing with optimistic locking (via @Version)
  - Retry mechanism for failed transactions
  - Rate limiting to prevent overload
  - Metrics collection with Micrometer and Prometheus

- **Database Layer**:
  - PostgreSQL for persistent storage
  - Connection pooling using HikariCP
  - Optimistic locking for concurrent transactions

- **Cache Layer**:
  - Redis for high-performance caching of account balances
  - Distributed rate limiting

- **Cloud Infrastructure**:
  - Kubernetes (EKS) for container orchestration
  - Horizontal Pod Autoscaling (HPA) for automatic scaling
  - AWS RDS for managed PostgreSQL database
  - AWS ElastiCache for managed Redis

## System Design

![System Architecture Diagram](https://i.imgur.com/abc123.png)

### Key Technical Decisions

1. **Optimistic Locking**: Uses versioning to handle concurrent transactions safely without blocking.
2. **Redis Caching**: Accounts are cached in Redis to minimize database load.
3. **Rate Limiting**: Prevents abuse and ensures fair access to the system.
4. **Transaction Tracking**: Each transaction has a unique ID and goes through various states (PENDING, PROCESSING, COMPLETED, FAILED, RETRYING).
5. **Kubernetes Deployment**: Ensures high availability and scalability.

## Features

- Real-time transaction processing
- Concurrent transaction handling with optimistic locking
- Distributed caching for improved performance
- Retry mechanism for failed transactions
- Rate limiting at account and API levels
- Comprehensive metrics and monitoring
- Extensive test coverage

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker and Docker Compose
- Kubernetes CLI (kubectl)
- AWS CLI (for AWS deployment)

## Local Development Setup

1. Clone the repository
   ```bash
   git clone https://github.com/your-username/easy-bank.git
   cd easy-bank
   ```

2. Install dependencies:
   ```bash
   mvn clean install
   ```

3. Start PostgreSQL and Redis locally:
   ```bash
   docker-compose up -d
   ```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

5. The API will be available at http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui/
   - Actuator: http://localhost:8080/actuator

## API Endpoints

### Account Operations

- `GET /api/accounts/{accountNumber}` - Get account details
- `POST /api/accounts` - Create a new account
- `POST /api/accounts/{sourceAccountNumber}/transfer` - Transfer money between accounts
- `GET /api/accounts/{accountNumber}/transactions` - Get account transactions

## Testing

### Unit Tests

Run the test suite:
```bash
mvn test
```

### Load Testing with JMeter

1. Start the application
2. Run the JMeter test:
   ```bash
   jmeter -n -t src/test/resources/jmeter/transaction-load-test.jmx -l results.jtl
   ```
3. Generate a report:
   ```bash
   jmeter -g results.jtl -o reports
   ```

## AWS Deployment

### Prerequisites

- AWS CLI configured with appropriate permissions
- EKS cluster created
- kubectl configured to connect to your EKS cluster

### Deployment Steps

1. Create an RDS PostgreSQL instance:
   ```bash
   aws rds create-db-instance \
     --db-instance-identifier easybank-db \
     --db-instance-class db.t3.micro \
     --engine postgres \
     --allocated-storage 20 \
     --master-username admin \
     --master-user-password <password> \
     --db-name easybank
   ```

2. Create an ElastiCache Redis cluster:
   ```bash
   aws elasticache create-cache-cluster \
     --cache-cluster-id easybank-cache \
     --engine redis \
     --cache-node-type cache.t3.micro \
     --num-cache-nodes 1
   ```

3. Update the ConfigMap and Secrets:
   ```bash
   kubectl apply -f k8s/config.yaml
   kubectl apply -f k8s/secrets.yaml
   ```

4. Build and push the Docker image:
   ```bash
   docker build -t <your-ecr-repo>/easybank-app:latest .
   docker push <your-ecr-repo>/easybank-app:latest
   ```

5. Deploy the application:
   ```bash
   kubectl apply -f k8s/database-resources.yaml
   kubectl apply -f k8s/cache-resources.yaml
   kubectl apply -f k8s/app-deployment.yaml
   kubectl apply -f k8s/app-service.yaml
   ```

6. Verify the deployment:
   ```bash
   kubectl get pods
   kubectl get services
   ```

## Monitoring

### Metrics Available

- Transaction processing time
- Success/failure rates
- Database operation times
- Cache hit/miss ratios
- Rate limiting metrics

### Prometheus Integration

Metrics are exposed at `/actuator/prometheus` and can be scraped by Prometheus.

Example Prometheus query to monitor transaction performance:
```
histogram_quantile(0.95, sum(rate(transaction_processing_time_seconds_bucket[5m])) by (le))
```

## Performance Considerations

- Optimistic locking for concurrent transactions
- Redis caching for frequently accessed account data
- Database connection pooling
- Kubernetes horizontal pod autoscaling
- Rate limiting to prevent system overload

## Security

- Secure communication using TLS
- Database encryption at rest
- AWS IAM roles for service accounts
- Network security groups and VPC configuration

## Operational Procedures

### Scaling

The application automatically scales based on CPU and memory utilization defined in the HPA configuration.

### Backup and Recovery

Database backups are automatically performed daily using the DB backup CronJob. Backups are stored in S3 for 30 days.

To restore from a backup:
```bash
aws s3 cp s3://<bucket>/backups/<backup-file> .
gunzip <backup-file>
psql -h <db-host> -U <db-user> -d easybank -f <backup-file>
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

This project is licensed under the MIT License.

## EasyBank Application

A robust banking application with transaction processing, rate limiting, and monitoring capabilities.

### Version 2.0.0 Release Notes

#### New Features
- Enhanced transaction validation with smart defaults
- Improved error handling and validation messages
- Rate limiting for API endpoints and transactions
- Prometheus metrics integration
- Kubernetes deployment support with resource optimization
- AWS deployment support with RDS and ElastiCache

#### Security Improvements
- Removed hardcoded credentials
- Environment variable based configuration
- Secure secret management in Kubernetes
- Rate limiting protection against abuse

#### Performance Optimizations
- Redis caching for frequently accessed data
- Optimized database queries
- Connection pooling improvements
- Resource-efficient Kubernetes deployment

### Quick Start

1. Set required environment variables:
```bash
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export DB_NAME=easybank
```

2. Start the application using Docker Compose:
```bash
docker-compose up -d
```

3. Or deploy to Kubernetes:
```bash
# First set up AWS services
./setup-aws-services.sh

# Then deploy the application
./deploy-to-aws.sh
```

### API Endpoints

#### Account Operations
- GET `/api/v1/accounts` - List accounts
- POST `/api/v1/accounts` - Create account
- GET `/api/v1/accounts/{id}` - Get account details
- PUT `/api/v1/accounts/{id}` - Update account
- DELETE `/api/v1/accounts/{id}` - Delete account

#### Transaction Operations
- POST `/api/v1/transactions` - Create transaction
- GET `/api/v1/transactions` - List transactions

### Configuration

The application uses the following default values which can be overridden:
- Account Types: "CHECKING" (default), "SAVINGS"
- Account Status: "ACTIVE" (default), "INACTIVE", "BLOCKED"
- Rate Limits: 600 requests/minute per IP, 100 transactions/minute per account

### Monitoring

Metrics available at `/actuator/prometheus`:
- Transaction processing time
- Success/failure rates
- Cache hit/miss ratios
- Rate limiting metrics

### Contributing

1. Fork the repository
2. Create a feature branch
3. Submit a pull request with tests

### License

This project is licensed under the MIT License.

## AWS Infrastructure Design

### Overview
EasyBank is deployed on AWS using a multi-VPC architecture with the following key components:
- Amazon EKS (Elastic Kubernetes Service) for container orchestration
- Amazon RDS (PostgreSQL) for persistent data storage
- Amazon ElastiCache (Redis) for caching and session management

### Network Architecture

#### VPC Layout
1. **EKS VPC (192.168.0.0/16)**
   - Contains EKS cluster and worker nodes
   - Multiple subnets across availability zones
   - Route tables configured for inter-VPC communication
   - Security groups controlling access to EKS nodes

2. **ElastiCache VPC (172.31.0.0/16)**
   - Contains Redis ElastiCache cluster
   - Default VPC subnets
   - Route tables updated for VPC peering
   - Security groups allowing Redis access

### VPC Peering Configuration
```
+------------------------+        Peering         +------------------------+
|     EKS VPC           | <------------------->  |   ElastiCache VPC     |
| (192.168.0.0/16)      |    Connection         |   (172.31.0.0/16)     |
|                       |                        |                        |
| +------------------+  |                        |  +------------------+ |
| |   EKS Cluster    |  |                        |  |    ElastiCache   | |
| |   Worker Nodes   |  |                        |  |    Redis Cluster | |
| +------------------+  |                        |  +------------------+ |
|                       |                        |                        |
+------------------------+                        +------------------------+
```

### Security Configuration

#### Security Groups
1. **EKS Node Security Group** (`<eks-security-group-id>`)
   - Outbound: Allow all traffic
   - Inbound: Allow traffic from within the security group
   - Inbound: Allow traffic from control plane

2. **ElastiCache Security Group** (`<cache-security-group-id>`)
   - Inbound: Allow TCP 6379 from EKS node security group
   - Outbound: Allow all traffic

### Network Routing

#### Route Tables
1. **EKS VPC Route Tables**
   ```
   Destination         Target
   192.168.0.0/16     local
   172.31.0.0/16      <vpc-peering-connection-id> (VPC Peering)
   0.0.0.0/0          <internet-gateway-id> (Internet Gateway)
   ```

2. **ElastiCache VPC Route Table**
   ```
   Destination         Target
   172.31.0.0/16      local
   192.168.0.0/16     <vpc-peering-connection-id> (VPC Peering)
   0.0.0.0/0          <internet-gateway-id> (Internet Gateway)
   ```

### Service Endpoints
- **PostgreSQL RDS**: `<rds-endpoint>:5432`
- **Redis ElastiCache**: `<elasticache-endpoint>:6379`

### Deployment Architecture
```
                                    ┌─────────────────┐
                                    │   Kubernetes    │
                                    │   Deployment    │
                                    └────────┬────────┘
                                             │
                                    ┌────────┴────────┐
                                    │    EKS Pods     │
                                    └────────┬────────┘
                                             │
                              ┌──────────────┴──────────────┐
                              │                             │
                     ┌────────┴────────┐           ┌───────┴────────┐
                     │  PostgreSQL RDS  │           │Redis ElastiCache│
                     └─────────────────┘           └────────────────┘
```

### Infrastructure Management
- VPC Peering managed through AWS CLI/Console
- Security groups and route tables configured via AWS CLI
- EKS cluster managed through `kubectl` and AWS EKS CLI
- Database and cache connections configured through Kubernetes ConfigMaps

### Network Flow
1. Application pods in EKS cluster initiate connections
2. Traffic to Redis flows through VPC peering connection
3. Security groups validate access permissions
4. Route tables direct traffic between VPCs
5. ElastiCache accepts connections from authorized security groups

### Best Practices Implemented
1. VPC isolation for different service types
2. Security group principle of least privilege
3. VPC peering for secure inter-VPC communication
4. Redundant route table entries for high availability
5. Clear CIDR block separation between VPCs

### Monitoring and Maintenance
- CloudWatch metrics for EKS and ElastiCache
- VPC Flow Logs for network troubleshooting
- Security group and route table auditing
- Regular validation of VPC peering status

## Application Components

### Backend (Spring Boot)
- Java 21 runtime
- Spring Boot 2.7.0
- PostgreSQL for persistent storage
- Redis for caching and rate limiting

### Infrastructure as Code
- Kubernetes manifests for deployment
- AWS CLI commands for network configuration
- Security group and route table definitions

## Development and Deployment

### Local Development
1. Clone the repository
2. Configure AWS credentials
3. Install required tools (AWS CLI, kubectl)
4. Set up local environment variables

### Production Deployment
1. Build Docker image
2. Push to Amazon ECR
3. Deploy to EKS cluster
4. Verify connectivity to Redis and PostgreSQL

## Troubleshooting

### Common Issues
1. VPC Peering Status
   ```bash
   aws ec2 describe-vpc-peering-connections
   ```

2. Security Group Rules
   ```bash
   aws ec2 describe-security-group-rules
   ```

3. Route Table Configuration
   ```bash
   aws ec2 describe-route-tables
   ```

4. Pod Connectivity
   ```bash
   kubectl exec -it <pod-name> -- nc -zv <redis-endpoint> 6379
   ```

### Health Checks
- Database connectivity
- Redis connection status
- VPC peering status
- Security group rule validation

## Security Considerations
- VPC isolation
- Security group restrictions
- No public endpoints for databases
- Encrypted communication
- Regular security audits
- **Never commit AWS account IDs, access keys, or other sensitive information to version control**
- Use AWS Secrets Manager or Parameter Store for sensitive configuration
- Use environment variables for local development

## Environment Variables
The following environment variables should be set:

```bash
# AWS Configuration
export AWS_REGION=<your-aws-region>
export AWS_ACCOUNT_ID=<your-aws-account-id>

# Database Configuration
export DB_HOST=<your-rds-endpoint>
export DB_PORT=5432
export DB_NAME=easybank

# Redis Configuration
export REDIS_HOST=<your-elasticache-endpoint>
export REDIS_PORT=6379
```

## Best Practices for Security
1. Use AWS IAM roles and policies with least privilege
2. Regularly rotate credentials
3. Enable AWS CloudTrail for audit logging
4. Use VPC Flow Logs for network monitoring
5. Implement AWS Config rules for security compliance
6. Never commit sensitive information to version control
7. Use `.gitignore` to prevent accidental commits of sensitive files