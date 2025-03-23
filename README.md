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