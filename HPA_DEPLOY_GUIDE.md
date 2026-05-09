# Workforce HPA 배포 및 테스트 가이드

## 목적

`k8s/hpa.yml`은 `4team` 네임스페이스의 백엔드 Deployment를 CPU 사용률 기준으로 자동 확장한다.

대상:

| HPA | Deployment | CPU target | min | max |
|---|---|---:|---:|---:|
| `gateway-hpa` | `gateway-depl` | 60% | 2 | 3 |
| `member-hpa` | `member-depl` | 60% | 2 | 3 |
| `salary-hpa` | `salary-depl` | 60% | 2 | 3 |
| `approval-hpa` | `approval-depl` | 60% | 2 | 3 |
| `goal-hpa` | `goal-depl` | 60% | 2 | 3 |
| `search-hpa` | `search-depl` | 60% | 2 | 3 |
| `ai-hpa` | `ai-depl` | 60% | 2 | 3 |

CPU 사용률은 각 컨테이너의 `resources.requests.cpu` 대비 현재 CPU 사용량으로 계산된다. 현재 서비스별 `depl_svc.yml`에는 CPU request가 이미 있으므로 HPA 계산이 가능하다.

## 사전 확인

### 1. 현재 클러스터 컨텍스트 확인

```powershell
kubectl config current-context
```

기대값:

```text
arn:aws:eks:ap-northeast-2:716482800486:cluster/my-cluster
```

### 2. metrics-server 확인

```powershell
kubectl get pods -n kube-system | Select-String metrics-server
kubectl top nodes
kubectl top pods -n 4team
```

`kubectl top`이 CPU/memory 값을 보여주면 HPA가 사용할 metrics API가 정상이다.

## HPA 배포

```powershell
kubectl apply -f k8s/hpa.yml
```

배포 확인:

```powershell
kubectl get hpa -n 4team
```

상세 확인:

```powershell
kubectl describe hpa gateway-hpa -n 4team
kubectl describe hpa member-hpa -n 4team
```

초기에는 `TARGETS`가 `<unknown>/60%`로 보일 수 있다. metrics-server 수집 주기 때문에 보통 1-2분 후 실제 값으로 바뀐다.

## 운영 상태 확인 명령어

전체 HPA:

```powershell
kubectl get hpa -n 4team -w
```

Deployment replica 확인:

```powershell
kubectl get deploy -n 4team
```

Pod 증가 확인:

```powershell
kubectl get pods -n 4team -w
```

리소스 사용량 확인:

```powershell
kubectl top pods -n 4team
```

## 안전한 HPA 동작 테스트

운영 서비스를 직접 부하로 때리기 전에, 테스트 전용 Deployment로 HPA 시스템이 정상 동작하는지 먼저 확인한다.

### 1. 테스트용 CPU 앱 생성

```powershell
kubectl create deployment hpa-cpu-demo -n 4team --image=registry.k8s.io/hpa-example
kubectl expose deployment hpa-cpu-demo -n 4team --port=80 --target-port=80
kubectl set resources deployment hpa-cpu-demo -n 4team --requests=cpu=100m --limits=cpu=500m
kubectl autoscale deployment hpa-cpu-demo -n 4team --cpu-percent=50 --min=1 --max=3
```

상태 확인:

```powershell
kubectl get hpa hpa-cpu-demo -n 4team
kubectl get deploy hpa-cpu-demo -n 4team
```

### 2. 부하 발생

별도 터미널에서 실행:

```powershell
kubectl run hpa-load-generator -n 4team --rm -it --restart=Never --image=busybox:1.36 -- /bin/sh -c "while true; do wget -q -O- http://hpa-cpu-demo.4team.svc.cluster.local; done"
```

다른 터미널에서 관찰:

```powershell
kubectl get hpa hpa-cpu-demo -n 4team -w
kubectl get pods -n 4team -l app=hpa-cpu-demo -w
```

CPU가 올라가면 `REPLICAS`가 `1`에서 `2` 또는 `3`으로 증가한다. 반영까지 1-3분 걸릴 수 있다.

### 3. 테스트 리소스 정리

부하 발생 터미널에서 `Ctrl + C`를 누른 뒤 정리한다.

```powershell
kubectl delete hpa hpa-cpu-demo -n 4team
kubectl delete svc hpa-cpu-demo -n 4team
kubectl delete deployment hpa-cpu-demo -n 4team
kubectl delete pod hpa-load-generator -n 4team --ignore-not-found
```

## 실제 서비스 HPA 테스트

실제 서비스는 인증, DB, 외부 API 호출이 섞여 있으므로 무작정 부하를 주면 비용이나 장애가 날 수 있다. 테스트는 짧게 진행한다.

### 1. 현재 상태 저장

```powershell
kubectl get hpa -n 4team
kubectl get deploy -n 4team
kubectl top pods -n 4team
```

### 2. Gateway 헬스 체크 부하 테스트

헬스 체크는 가볍기 때문에 반드시 scale-out이 발생하지 않을 수 있다. 그래도 요청 경로와 HPA 관찰 흐름을 검증하는 용도로 사용할 수 있다.

```powershell
kubectl run gateway-load-generator -n 4team --rm -it --restart=Never --image=busybox:1.36 -- /bin/sh -c "while true; do wget -q -O- http://gateway-service.4team.svc.cluster.local/actuator/health; done"
```

관찰:

```powershell
kubectl get hpa gateway-hpa -n 4team -w
kubectl top pods -n 4team | Select-String gateway
kubectl get pods -n 4team -l app=gateway
```

정리:

```powershell
kubectl delete pod gateway-load-generator -n 4team --ignore-not-found
```

### 3. 외부 도메인 부하 테스트

PC에 `hey`가 있다면 짧게만 실행한다.

```powershell
hey -z 60s -c 20 https://server.workforcehr.shop/actuator/health
```

관찰:

```powershell
kubectl get hpa -n 4team -w
kubectl top pods -n 4team
```

## HPA 삭제 또는 롤백

HPA만 제거:

```powershell
kubectl delete -f k8s/hpa.yml
```

수동 replica를 무중단 배포 기본값인 2개로 복구:

```powershell
kubectl scale deployment gateway-depl -n 4team --replicas=2
kubectl scale deployment member-depl -n 4team --replicas=2
kubectl scale deployment salary-depl -n 4team --replicas=2
kubectl scale deployment approval-depl -n 4team --replicas=2
kubectl scale deployment goal-depl -n 4team --replicas=2
kubectl scale deployment search-depl -n 4team --replicas=2
kubectl scale deployment ai-depl -n 4team --replicas=2
```

## 트러블슈팅

### TARGETS가 `<unknown>`으로 표시됨

확인:

```powershell
kubectl top pods -n 4team
kubectl get apiservice v1beta1.metrics.k8s.io
kubectl logs -n kube-system deploy/metrics-server
```

주요 원인:

- metrics-server가 아직 수집 전
- 대상 Pod에 CPU request가 없음
- metrics-server Pod 문제

### Pod가 늘어나지 않음

확인:

```powershell
kubectl describe hpa gateway-hpa -n 4team
kubectl top pods -n 4team
kubectl describe deployment gateway-depl -n 4team
```

주요 원인:

- CPU가 target보다 낮음
- `maxReplicas`에 이미 도달함
- 노드 리소스 부족으로 새 Pod 스케줄링 실패
- readinessProbe 통과 전이라 Ready replica가 늦게 반영됨

### 부하 종료 후 Pod가 바로 줄지 않음

정상이다. `k8s/hpa.yml`에는 scale down 안정화를 위해 `stabilizationWindowSeconds: 300`이 들어가 있다. 부하가 사라져도 약 5분 정도는 급격히 줄이지 않는다.

### 운영 권장값 조정

현재 값은 보수적인 기본값이다.

- 트래픽이 자주 튀면 `maxReplicas`를 5 이상으로 증가
- 너무 민감하게 늘어나면 `averageUtilization`을 70으로 증가
- 더 빠르게 늘려야 하면 `scaleUp.policies.value`를 2로 증가
- 비용을 줄이고 싶으면 `maxReplicas`를 2로 감소
