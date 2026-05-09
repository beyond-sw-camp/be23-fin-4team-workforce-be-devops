# Workforce K8s 배포 가이드

## 배포 환경

- **인프라**: AWS EKS + ECR + RDS (MariaDB) + S3
- **CI/CD**: GitHub Actions
- **네임스페이스**: `4team`
- **백엔드 도메인**: `service.workforcehr.shop` (수정 사항)
- **프론트엔드 도메인**: `www.workforcehr.shop` (S3 + CloudFront)
- **리전**: `ap-northeast-2`

## 사전 준비

### 1. EKS 클러스터 생성
EKS 클러스터를 생성한 후 클러스터 이름을 다음 위치에 입력:
- `.github/workflows/deploy.yml` → `<YOUR_EKS_CLUSTER_NAME>`

### 2. ECR 레포지토리 생성
다음 8개 레포지토리 생성:
- `4team/gateway`
- `4team/member`
- `4team/salary`
- `4team/approval`
- `4team/goal`
- `4team/search`
- `4team/ai`

생성 후 본인 AWS 계정 ID를 다음 위치에 입력:
- `.github/workflows/deploy.yml` → `<YOUR_ACCOUNT_ID>`
- 각 서비스 `k8s/depl_svc.yml` → `<YOUR_ACCOUNT_ID>`

### 3. RDS 생성 (MariaDB)
- DB 이름: `workforce`
- 단일 schema 사용

### 4. S3 버킷 생성
- `workforce-profiles` (프로필 이미지)
- `workforce-approval` (결재 첨부)

### 5. cert-manager + ingress-nginx 설치
```bash
# cert-manager 설치
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.3/cert-manager.yaml

# ingress-nginx 설치
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/aws/deploy.yaml
```

### 6. 네임스페이스 생성
```bash
kubectl create namespace 4team
```

### 7. Secret 생성 (kubectl 직접 생성)

```bash
kubectl create secret generic workforce-secrets \
  --namespace=4team \
  --from-literal=DB_HOST=<RDS_ENDPOINT> \
  --from-literal=DB_PORT=3306 \
  --from-literal=DB_NAME=workforce \
  --from-literal=DB_USER=<RDS_USERNAME> \
  --from-literal=DB_PASSWORD=<RDS_PASSWORD> \
  --from-literal=JWT_SECRET_AT=<YOUR_JWT_AT_SECRET> \
  --from-literal=JWT_SECRET_RT=<YOUR_JWT_RT_SECRET> \
  --from-literal=AWS_ACCESS_KEY=<AWS_ACCESS_KEY> \
  --from-literal=AWS_SECRET_KEY=<AWS_SECRET_KEY> \
  --from-literal=MAIL_USERNAME=<GMAIL_ADDRESS> \
  --from-literal=MAIL_PASSWORD=<GMAIL_APP_PASSWORD> \
  --from-literal=OPENAI_API_KEY=<OPENAI_API_KEY> \
  --from-literal=PINECONE_API_KEY=<PINECONE_API_KEY> \
  --from-literal=PINECONE_INDEX_NAME=workforce-hr \
  --from-literal=NTS_API_KEY=<NTS_API_KEY> \
  --from-literal=HOLIDAY_API_KEY=<HOLIDAY_API_KEY>
```

### 8. GitHub Actions Secrets 설정
GitHub Repository → Settings → Secrets and variables → Actions:
- `AWS_KEY` (AWS Access Key)
- `AWS_SECRET` (AWS Secret Key)

## 배포 순서

### Step 1: 인프라 배포
```bash
# 인프라 (Kafka, Redis, Redis-Stack, Elasticsearch, n8n)
kubectl apply -f k8s/kafka.yml
kubectl apply -f k8s/redis.yml
kubectl apply -f k8s/redis-stack.yml
kubectl apply -f k8s/elasticsearch.yml
kubectl apply -f k8s/n8n.yml

# Pod 정상 동작 확인
kubectl get pods -n 4team
```

### Step 2: cert-manager 설정 (도메인 결정 후)
```bash
kubectl apply -f k8s/cert-manager.yml
```

### Step 3: GitHub에 push
main 브랜치에 push하면 GitHub Actions가 자동으로:
1. 8개 서비스 Docker 이미지 빌드
2. ECR push
3. EKS에 배포

### Step 4: Ingress 적용 (TLS 인증서 발급 후)
```bash
kubectl apply -f k8s/ingress.yml
```

### Step 5: HPA 적용 (선택)
```bash
kubectl apply -f k8s/hpa.yml
kubectl get hpa -n 4team
```

상세한 배포/테스트 절차는 `HPA_DEPLOY_GUIDE.md` 참고.

### Step 6: PodDisruptionBudget 적용 (무중단 배포 보호)
```bash
kubectl apply -f k8s/pdb.yml
kubectl get pdb -n 4team
```

Deployment RollingUpdate, HPA, PDB를 포함한 무중단 배포 검증 절차는 `ZERO_DOWNTIME_DEPLOY_GUIDE.md` 참고.

### Step 7: n8n 워크플로 import
- n8n 접속 후 워크플로 JSON 파일을 import

## 서비스 구성

| 서비스 | 포트 | DNS |
|------|------|-----|
| gateway | 8080 | gateway-service:80 |
| member-service | 8080 | member-service:8080 |
| salary-service | 8080 | salary-service:8080 |
| approval-service | 8080 | approval-service:8080 |
| goal-service | 8080 | goal-service:8080 |
| search-service | 8080 | search-service:8080 |
| ai-service | 8090 | ai-service:8090 |
| n8n | 5678 | n8n-service:5678 |
| kafka | 9092 | kafka-service:9092 |
| redis | 6379 | redis-service:6379 |
| redis-stack | 6379 → 6380 | redis-stack-service:6380 |
| elasticsearch | 9200 | elasticsearch-service:9200 |

## 변경된 점 (로컬 → K8s)

1. **Eureka 제거**: K8s Service DNS 사용 (`http://member-service:8080`)
2. **Gateway 라우팅**: `lb://member-service` → `http://member-service:8080`
3. **모든 외부 의존성 (DB, Kafka, Redis 등)**: 환경변수 주입 (`${DB_HOST}` 등)
4. **하드코딩된 시크릿 제거**: K8s Secret으로 분리
5. **Spring Boot Actuator 추가**: `/actuator/health` 엔드포인트로 헬스체크
6. **n8n webhook URL**: `http://localhost:5678` → `http://n8n-service:5678`
7. **ai-service GATEWAY_URL**: 환경변수로 주입

## 트러블슈팅

### 무중단 배포 상태 확인
```bash
kubectl rollout status deployment/gateway-depl -n 4team
kubectl get pods -n 4team -l app=gateway -w
kubectl get pdb -n 4team
```

배포 중 `maxUnavailable: 0`, `maxSurge: 1` 설정으로 새 Pod가 Ready 된 뒤 기존 Pod가 종료되어야 한다.

### HPA 상태 확인
```bash
kubectl get hpa -n 4team
kubectl describe hpa gateway-hpa -n 4team
kubectl top pods -n 4team
```

`TARGETS`가 `<unknown>`이면 metrics-server 상태와 Pod CPU request 설정을 확인한다.

### Pod가 CrashLoopBackOff
```bash
kubectl logs -n 4team <pod-name>
kubectl describe pod -n 4team <pod-name>
```

### Kafka 연결 실패
- KRaft 모드 사용 중 (Zookeeper 불필요)
- `KAFKA_ADVERTISED_LISTENERS`가 K8s Service DNS와 일치해야 함

### DB 연결 실패
- RDS 보안그룹에서 EKS 노드 IP 허용 필요
- `DB_HOST` Secret에 RDS endpoint 정확히 입력

### Ingress 인증서 발급 안 됨
- 도메인 DNS가 AWS LB로 정확히 연결됐는지 확인
- cert-manager가 정상 동작하는지: `kubectl get clusterissuer`

---

## 로컬 개발 환경 실행 방법

### 환경 구성
- 로컬에서는 **Eureka 사용** (`localhost:8761`)
- 모든 서비스 포트는 `0` (랜덤) → Eureka로 디스커버리
- 인프라(Kafka, Redis, MariaDB)는 로컬에 직접 실행 또는 docker-compose로 띄움

### IntelliJ에서 실행
각 서비스의 Run Configuration에서 환경변수 추가:
```
SPRING_PROFILES_ACTIVE=local
```

### 실행 순서 (반드시 이 순서로)
1. 인프라 (MariaDB, Redis, Redis-Stack, Kafka, Elasticsearch) 실행
2. **eureka** (포트 8761) — 가장 먼저
3. **gateway** (포트 8080)
4. 나머지 서비스 (member-service, salary-service, approval-service, goal-service, search-service)
5. **ai-service** (별도 터미널, 포트 8090)
   ```bash
   cd ai-service
   python3.11 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   uvicorn app.main:app --host 0.0.0.0 --port 8090
   ```
6. **n8n** (포트 5678)
   ```bash
   docker run -p 5678:5678 -v ~/.n8n:/home/node/.n8n n8nio/n8n
   ```

### 로컬 vs K8s 차이

| 항목 | 로컬 | K8s |
|------|------|-----|
| Eureka | 사용 (8761) | 비활성화 |
| 서비스 포트 | 0 (랜덤) | 8080 고정 |
| Gateway 라우팅 | `lb://service-name` | `http://service-name:8080` |
| n8n URL | `localhost:5678` | `n8n-service:5678` |
| DB | `localhost:3306` | RDS endpoint |
| Profile | `local` | `prod` |
