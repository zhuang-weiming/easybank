# Easy Bank - Real-Time Balance Calculation System

A high-performance, resilient banking system built with Spring Boot and deployed on AWS EKS.

## Architecture Overview

The system is designed with the following components:

- **Spring Boot Backend**: Handles transaction processing and account management
- **PostgreSQL Database**: Stores account and transaction data
- **Redis Cache**: Provides high-performance caching for account balances
- **AWS Infrastructure**:
  - EKS for container orchestration
  - RDS for managed PostgreSQL database
  - ElastiCache for managed Redis cache

## Features

- Real-time transaction processing
- Concurrent transaction handling with optimistic locking
- Distributed caching for improved performance
- Retry mechanism for failed transactions
- Comprehensive test coverage

## Prerequisites

- Java 11
- Maven
- Docker
- AWS CLI
- kubectl

## Local Development Setup

1. Clone the repository
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

## API Endpoints

### Account Operations

- `GET /api/accounts/{accountNumber}` - Get account details
- `POST /api/accounts/{sourceAccountNumber}/transfer` - Transfer money between accounts
- `GET /api/accounts/{accountNumber}/transactions` - Get account transactions

## Testing

Run the test suite:
```bash
mvn test
```

## AWS Deployment

1. Create EKS cluster
2. Set up RDS instance
3. Configure ElastiCache
4. Deploy application using Kubernetes manifests

Detailed AWS deployment instructions will be added in subsequent updates.

## Performance Considerations

- Optimistic locking for concurrent transactions
- Redis caching for frequently accessed account data
- Database connection pooling
- Kubernetes horizontal pod autoscaling

## Security

- Secure communication using TLS
- Database encryption at rest
- AWS IAM roles for service accounts
- Network security groups and VPC configuration

## Monitoring

- Kubernetes metrics
- Application metrics using Prometheus
- Logging with ELK stack
- AWS CloudWatch integration

## Contributing

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

This project is licensed under the MIT License.