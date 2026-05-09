# Workforce 무중단 배포 가이드

## 적용 내용

백엔드 7개 서비스에 RollingUpdate 기반 무중단 배포 설정을 추가했다.

대상:

- `gateway-depl`
- `member-depl`
- `salary-depl`
- `approval-depl`
- `goal-depl`
- `search-depl`
- `ai-depl`

각 Deployment 공통 설정:

```yaml
replicas: 2
minReadySeconds: 10
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
terminationGracePeriodSeconds: 60
```

의미:

- `replicas: 2`: 기본 Pod를 2개 유지한다.
- `maxUnavailable: 0`: 새 Pod가 Ready 되기 전 기존 Pod를 내리지 않는다.
- `maxSurge: 1`: 배포 중 임시로 Pod를 1개 더 띄울 수 있다.
- `minReadySeconds: 10`: 새 Pod가 Ready 상태로 10초 이상 유지되어야 배포 성공으로 본다.
- `terminationGracePeriodSeconds: 60`: 종료 중인 Pod가 요청을 마무리할 시간을 준다.

## HPA와의 관계

무중단 배포를 위해 `k8s/hpa.yml`의 `minReplicas`도 `2`로 맞췄다. HPA가 적용된 상태에서 `minReplicas: 1`이면 배포 파일의 `replicas: 2`를 적용해도 HPA가 다시 1개로 줄일 수 있다.

## PodDisruptionBudget

`k8s/pdb.yml`을 추가했다. 노드 드레인, 클러스터 점검, 일부 자발적 중단 상황에서 서비스별 최소 1개 Pod가 남도록 보호한다.

```powershell
kubectl apply -f k8s/pdb.yml
kubectl get pdb -n 4team
```

주의: PDB는 Deployment RollingUpdate 자체를 대신하지 않는다. RollingUpdate는 Deployment strategy가 담당하고, PDB는 노드 작업 같은 자발적 중단 상황을 보호한다.

## 적용 순서

### 1. Deployment 설정 적용

서비스별 배포 파일을 적용한다.

```powershell
kubectl apply -f gateway/k8s/depl_svc.yml
kubectl apply -f member-service/k8s/depl_svc.yml
kubectl apply -f salary-service/k8s/depl_svc.yml
kubectl apply -f approval-service/k8s/depl_svc.yml
kubectl apply -f goal-service/k8s/depl_svc.yml
kubectl apply -f search-service/k8s/depl_svc.yml
kubectl apply -f ai-service/k8s/depl_svc.yml
```

### 2. HPA 적용

```powershell
kubectl apply -f k8s/hpa.yml
kubectl get hpa -n 4team
```

### 3. PDB 적용

```powershell
kubectl apply -f k8s/pdb.yml
kubectl get pdb -n 4team
```

### 4. Pod 2개 유지 확인

```powershell
kubectl get deploy -n 4team
kubectl get pods -n 4team
```

각 서비스의 `READY`가 `2/2`가 되는지 확인한다.

## 무중단 배포 테스트

### 1. Gateway 배포 감시 터미널

```powershell
kubectl rollout status deployment/gateway-depl -n 4team --watch
```

### 2. Pod 상태 감시 터미널

```powershell
kubectl get pods -n 4team -l app=gateway -w
```

배포 중 기대 흐름:

```text
기존 Pod 2개 Running
새 Pod 1개 생성
새 Pod Ready
기존 Pod 1개 Terminating
다시 새 Pod 1개 생성
최종 Pod 2개 Running
```

중간에 Ready Pod가 0개가 되면 안 된다.

### 3. 요청 연속 테스트 터미널

PowerShell에서 1초마다 외부 도메인 health check를 호출한다.

```powershell
while ($true) {
  try {
    $status = (Invoke-WebRequest -Uri "https://server.workforcehr.shop/actuator/health" -UseBasicParsing -TimeoutSec 5).StatusCode
    "$(Get-Date -Format HH:mm:ss) $status"
  } catch {
    "$(Get-Date -Format HH:mm:ss) ERROR $($_.Exception.Message)"
  }
  Start-Sleep -Seconds 1
}
```

### 4. 롤링 재시작 실행

다른 터미널에서 실행한다.

```powershell
kubectl rollout restart deployment/gateway-depl -n 4team
```

성공 기준:

- `rollout status`가 성공한다.
- 요청 연속 테스트에서 `200`이 유지된다.
- `kubectl get pods -l app=gateway`에서 Ready Pod가 1개 이상 계속 유지된다.

## 전체 서비스 롤아웃 상태 확인

```powershell
kubectl rollout status deployment/gateway-depl -n 4team
kubectl rollout status deployment/member-depl -n 4team
kubectl rollout status deployment/salary-depl -n 4team
kubectl rollout status deployment/approval-depl -n 4team
kubectl rollout status deployment/goal-depl -n 4team
kubectl rollout status deployment/search-depl -n 4team
kubectl rollout status deployment/ai-depl -n 4team
```

## 롤백

가장 최근 ReplicaSet으로 롤백:

```powershell
kubectl rollout undo deployment/gateway-depl -n 4team
```

롤아웃 이력 확인:

```powershell
kubectl rollout history deployment/gateway-depl -n 4team
```

특정 revision으로 롤백:

```powershell
kubectl rollout undo deployment/gateway-depl -n 4team --to-revision=<REVISION>
```

## 비용/리소스 주의

무중단 배포를 위해 기본 replica가 1개에서 2개로 늘어난다. 배포 중에는 `maxSurge: 1` 때문에 서비스별 최대 3개 Pod까지 잠시 올라갈 수 있다.

현재 HPA도 `minReplicas: 2`, `maxReplicas: 3`이다. 비용을 더 줄이고 싶다면 HPA를 적용하지 않고 수동 replica만 2로 유지하거나, 일부 중요 서비스만 2개로 운영한다.

## 한계

이 설정은 Kubernetes 레벨의 무중단 배포를 보장하는 구성이다. 다음 경우에는 애플리케이션 레벨 대응도 필요하다.

- DB migration이 하위 호환되지 않는 경우
- WebSocket, SSE, 장시간 요청이 종료 시 graceful shutdown을 지원하지 않는 경우
- 새 버전과 구 버전이 같은 Kafka 메시지를 다르게 해석하는 경우
- readinessProbe가 실제 의존성 상태를 충분히 반영하지 못하는 경우
