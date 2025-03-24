# Easy Bank - Real-Time Balance Calculation System

A high-performance banking system built with Spring Boot and deployed on AWS EKS, optimized for t3.micro instance constraints.

## Quick Links
- [Architecture](#architecture)
- [Features](#features)
- [Infrastructure](#infrastructure)
- [Development](#development)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Known Limitations](#known-limitations)

## Architecture

### System Components
- **Spring Boot Backend**: RESTful API with optimistic locking
- **PostgreSQL**: Persistent storage with connection pooling
- **Redis**: High-performance caching and rate limiting
- **AWS Infrastructure**: EKS, RDS, ElastiCache

### System Design
```
                    [AWS Application Load Balancer]
                              │
                    [EKS Cluster (t3.micro)]
                              │
                    [Single EasyBank Pod]
                              │
                 ┌────────────┴────────────┐
                 │                         │
        [PostgreSQL RDS]           [Redis ElastiCache]
```

### Key Technical Decisions
1. **Optimistic Locking**: Version-based concurrency control
2. **Redis Caching**: Minimize database load
3. **Rate Limiting**: Prevent system overload
4. **Resource Optimization**: Configured for t3.micro constraints

## Features

### Core Functionality
- Real-time transaction processing
- Concurrent transaction handling
- Distributed caching
- Retry mechanism for failed transactions
- Rate limiting at account and API levels

### Technical Features
- Comprehensive metrics collection
- Extensive test coverage
- Health monitoring
- Performance optimization
- Security hardening

## Infrastructure

### AWS Services
- **EKS**: Single node t3.micro cluster
- **RDS**: PostgreSQL 14 (db.t3.micro)
- **ElastiCache**: Redis 7.2 (cache.t3.micro)
- **Region**: us-west-2

### Resource Configuration
```yaml
# Application Pod (t3.micro)
resources:
  requests:
    cpu: "100m"      # 5% of total CPU
    memory: "256Mi"  # 25% of total memory
  limits:
    cpu: "200m"      # 10% of total CPU
    memory: "384Mi"  # 37.5% of total memory

# Connection Pools
spring:
  datasource:
    hikari:
      maximum-pool-size: 3
      minimum-idle: 1
  redis:
    pool:
      max-active: 5
      max-idle: 2
      min-idle: 1

# Rate Limits
max-requests-per-minute: 300
max-transactions-per-minute: 50
```

### Network Security
- Multi-VPC architecture
- VPC Peering enabled
- Private subnets for databases
- Security groups with least privilege

## Development

### Prerequisites
- Java 21 (OpenJDK)
- Maven 3.6+
- Docker Desktop
- kubectl
- AWS CLI

### Local Setup
```bash
# Clone repository
git clone https://github.com/zhuang-weiming/easybank
cd easy-bank

# Set environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/easybank
export SPRING_DATASOURCE_USERNAME=yourusername
export SPRING_DATASOURCE_PASSWORD=yourpassword
export SPRING_REDIS_HOST=localhost
export SPRING_REDIS_PORT=6379

# Start dependencies and application
docker-compose up -d
mvn clean install
mvn spring-boot:run
```

### API Endpoints
- `GET /api/accounts/{accountNumber}` - Get account details
- `POST /api/accounts` - Create account
- `POST /api/accounts/{sourceAccountNumber}/transfer` - Transfer money
- `GET /api/accounts/{accountNumber}/transactions` - Get transactions

### Testing
```bash
# Unit Tests
mvn test

# Load Testing
jmeter -n -t src/test/resources/jmeter/account-query-test.jmx -l results.jtl -e -o report
```

## Deployment

### AWS Setup
```bash
# Create single-node cluster
eksctl create cluster \
  --name easybank-cluster \
  --region us-west-2 \
  --node-type t3.micro \
  --nodes 1 \
  --managed=false \
  --without-nodegroup

# Add node group
eksctl create nodegroup \
  --cluster easybank-cluster \
  --region us-west-2 \
  --name easybank-workers \
  --node-type t3.micro \
  --nodes 1 \
  --nodes-min 1 \
  --nodes-max 1
```

### Application Deployment
```bash
# Apply configurations
kubectl apply -f k8s/config.yaml
kubectl apply -f k8s/app-deployment.yaml
```

## Monitoring

### Health Checks
- `/actuator/health` - Application health
- `/actuator/prometheus` - Metrics endpoint

### Resource Monitoring
```bash
# Check pod resources
kubectl top pod <pod-name>

# View pod logs
kubectl logs <pod-name>
```

### Performance Test Results

#### API Response Tests
```bash
# Health Check
curl -I http://[load-balancer-url]/actuator/health
HTTP/1.1 200 
Content-Type: application/vnd.spring-boot.actuator.v3+json

# Account Creation Test
curl -X POST http://[load-balancer-url]/api/accounts \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     -d 'accountHolder=John Doe&accountType=SAVINGS&currency=USD&initialBalance=1000.00'
Response: Account created successfully with number ACC-f6ecf037

# Account Query Test
curl -X GET http://[load-balancer-url]/api/accounts/ACC-f6ecf037
Response: Account details retrieved successfully

# Money Transfer Test
curl -X POST 'http://[load-balancer-url]/api/accounts/ACC-f6ecf037/transfer?destinationAccountNumber=ACC-323a5105&amount=59'
Response: Transfer completed successfully with transaction ID 5ed3050a-0515-4b2b-a805-c65d7c73733e
```

#### Load Test Results (JMeter)

**Test Configuration:**
- Duration: 3 minutes 24 seconds
- Request Rate: 1 request per second
- Endpoint: GET /api/accounts/{accountNumber}

**Performance Metrics:**
- Total Requests: 204
- Error Rate: 0%
- Average Response Time: 607ms
- Min Response Time: 551ms
- Max Response Time: 834ms
- Throughput: 1.0 requests/second

**Response Time Distribution:**
```
Time Period     Requests    Avg Response    Min    Max    Errors
00:00-00:24    24          617ms           553    805    0
00:24-00:54    30          617ms           568    834    0
00:54-01:24    30          612ms           556    687    0
01:24-01:54    30          601ms           557    686    0
01:54-02:24    30          606ms           563    684    0
02:24-02:54    30          607ms           552    706    0
02:54-03:24    30          594ms           551    672    0
```

**Resource Usage**

Pod Resource Metrics:
```
NAME                            CPU(cores)   MEMORY(bytes)
easybank-app-68c69c477b-n4th9   10m          375Mi
```

Resource Utilization:
- Memory Usage: 375Mi out of 384Mi limit (97.6%)
- CPU Usage: 10m (0.01 cores or 1% of CPU)
- Pod Status: Stable under test load

## Known Limitations

### t3.micro Constraints
```
Memory (1024Mi):
- System Reserved: ~200Mi
- Kubernetes Components: ~200Mi
- Available for Pods: ~624Mi
- Current Pod Usage: 375Mi (97.6%)
```

### HPA Issues
```
WARNING: DO NOT ENABLE HPA with t3.micro

Problem: Enabling HPA causes pod restart loops due to:
- Limited memory (1GB total)
- Multiple pods cannot fit in available memory
- Results in OOM kills and service instability
```

### Best Practices
1. Maintain single replica deployment
2. Monitor memory usage (97.6% utilized)
3. Use specified resource limits
4. Consider t3.small for scaling needs

## Security

### Application Security
- Rate limiting enabled
- API authentication required
- Secrets in K8s Secrets
- TLS encryption enabled

### Database Security
- Private subnet access
- Encryption at rest
- TLS connections
- Regular backups

### Network Security
- VPC isolation
- Security group restrictions
- No public endpoints for databases
- Encrypted communication

## Performance Optimization

### Memory Optimization
- Tune JVM heap settings
- Implement efficient caching
- Optimize object serialization

### Performance Tuning
- Implement query optimization
- Add database indexing
- Optimize cache usage
- Monitor memory patterns

### Infrastructure Optimization
- Consider t3.small upgrade
- Implement horizontal scaling
- Enable auto-scaling

## Backup & Recovery

### Database Backups
- Automated daily backups
- Retention period: 30 days
- Backup window: 03:00-04:00 UTC
- Storage: AWS S3

### Recovery Procedures
1. Point-in-time recovery available
2. Manual snapshot restoration
3. Cross-region replication: Not configured
