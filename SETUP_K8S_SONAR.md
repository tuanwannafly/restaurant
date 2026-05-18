# Kubernetes + SonarQube — Hướng dẫn tích hợp

## Cấu trúc thư mục mới

```
smart-restaurant/
├── k8s/
│   ├── base/                   ← Config dùng chung
│   │   ├── configmap.yaml
│   │   ├── secret.yaml         ← KHÔNG commit giá trị thật!
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── hpa.yaml            ← Auto-scaling
│   │   └── kustomization.yaml
│   └── overlays/
│       ├── dev/
│       └── prod/               ← Ghi đè replica + image tag
│           └── kustomization.yaml
├── sonar/
│   ├── sonar-project.properties
│   └── quality-gate.json
├── .github/
│   └── workflows/
│       └── ci.yml              ← Pipeline: Build → Sonar → Deploy
└── pom.xml                     ← Đã thêm JaCoCo + Sonar plugin
```

---

## 1. Chạy SonarQube local (Docker)

```bash
# Khởi động SonarQube Community
docker run -d --name sonarqube \
  -p 9000:9000 \
  sonarqube:10-community

# Truy cập: http://localhost:9000
# Login mặc định: admin / admin
```

Sau khi login, tạo project → lấy token → chạy scan:

```bash
./mvn verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN
```

---

## 2. Deploy lên Kubernetes local (minikube / kind)

```bash
# Tạo namespace
kubectl create namespace smart-restaurant-prod

# Tạo secret (thay các giá trị thật)
kubectl create secret generic smart-restaurant-secret \
  --from-literal=DB_URL='jdbc:oracle:thin:@host:1521/XEPDB1' \
  --from-literal=DB_USERNAME='restaurant' \
  --from-literal=DB_PASSWORD='secret' \
  --from-literal=MAIL_USERNAME='you@gmail.com' \
  --from-literal=MAIL_PASSWORD='app_password' \
  -n smart-restaurant-prod

# Build image và load vào minikube
eval $(minikube docker-env)
docker build -t smart-restaurant:latest .

# Deploy
kubectl apply -k k8s/overlays/prod

# Kiểm tra
kubectl get all -n smart-restaurant-prod
kubectl get hpa  -n smart-restaurant-prod
```

---

## 3. Thiết lập GitHub Actions CI/CD

Thêm các Secrets vào GitHub repository (Settings → Secrets):

| Secret | Giá trị |
|--------|---------|
| `SONAR_TOKEN` | Token từ SonarCloud/SonarQube |
| `SONAR_HOST_URL` | `https://sonarcloud.io` hoặc URL self-hosted |
| `KUBECONFIG` | `base64 ~/.kube/config` |

Pipeline sẽ tự động:
1. **Build + Test** với Maven
2. **Scan SonarQube** — block merge nếu Quality Gate FAIL
3. **Build Docker image** và push lên GHCR
4. **Deploy K8s** khi merge vào `main` (cần approval)

---

## 4. Giải thích với nhà tuyển dụng ngân hàng

| Yêu cầu JD | Giải pháp trong project |
|------------|------------------------|
| **Kubernetes** | Deployment + HPA tự scale 2–10 pod theo CPU/RAM |
| **SonarQube** | Quality Gate chuẩn banking: 0 bug mới, 100% hotspot review, coverage ≥70% |
| **Security** | Secret không hardcode, RBAC đã có trong code (`session/RbacGuard.java`) |
| **Scalability** | HPA + `topologySpreadConstraints` phân tán Pod ra nhiều Node |
| **Zero-downtime** | `RollingUpdate` + `maxUnavailable: 0` |
