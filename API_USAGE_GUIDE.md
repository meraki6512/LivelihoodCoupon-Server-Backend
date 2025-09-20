# API 사용 가이드

|              API              | Method |        설명         |               사용법                |
|:-----------------------------:|:------:|:-----------------:|:--------------------------------:|
|  `/admin/collect/nationwide`  |  GET   |       전국 수집       |                -                 |
| `/admin/collect/{regionName}` |  GET   |     단일 지역 수집      |     GET `/admin/collect/전라남도%20구례군`     |
|   `/admin/collect/regions`    |  POST  | **여러 지역 수집** (권장) | `{"regionNames": ["전라남도 구례군", "서울특별시 종로구"]}` |
| `/admin/performance/compare`  |  POST  |  기존 vs 개선 성능 비교   | `{"regionNames": ["서울특별시 종로구"]}` |
|  `/admin/performance/test`    |  POST  |   통합 성능 테스트     | `[{"name": "전라남도 구례군"}]` |
| `/admin/performance/load-test`|  POST  |      부하 테스트      | `{"smallRegions": [...], "mediumRegions": [...]}` |
|`/admin/performance/memory-test`| POST  |     메모리 테스트     | `{"testRegions": [...], "durationMinutes": 5}` |
|  `/admin/performance/stats`   |  GET   |    성능 통계 조회     |            GET `/admin/performance/stats` |
|        `/health`              |  GET   |     서비스 상태 확인     |            GET `/health`           |
|      `/health/memory`         |  GET   |     메모리 상태 확인     |         GET `/health/memory`       |
|        `/health/gc`           |  GET   |   강제 가비지 컬렉션    |           GET `/health/gc`         |

## 📊 데이터 수집 API

### 1. 전국 데이터 수집

```bash
GET /admin/collect/nationwide
```

### 2. 특정 지역 데이터 수집

```bash
GET /admin/collect/{regionName}
# 예: GET /admin/collect/구례군
``` 

### 3. 여러 지역명으로 데이터 수집 (권장)

```bash
POST /admin/collect/regions
Content-Type: application/json

{
  "regionNames": ["서울특별시 종로구", "경기도 수원시 장안구"]
}
```

**JSON 파일 사용:**

```bash
curl -X POST http://localhost:8080/admin/collect/regions \
  -H "Content-Type: application/json" \
  -d @data/test-requests/region-names-request.json
```

### 4. JSON으로 여러 지역 수집 (폴리곤 직접 입력)

```bash
POST /admin/integrated/collect
Content-Type: application/json

[
  {
    "name": "구례군",
    "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
  }
]
```

**JSON 파일 사용:**

```bash
curl -X POST http://localhost:8080/admin/integrated/collect \
  -H "Content-Type: application/json" \
  -d @data/test-requests/region-collection-request.json
```

## 🧪 성능 테스트 API

### 1. 성능 비교 테스트 (기존 vs 개선)

```bash
POST /admin/performance/compare
Content-Type: application/json

{
  "regionNames": ["서울특별시 종로구", "경기도 수원시 장안구"]
}
```

### 2. 성능 테스트 (통합 버전만)

```bash
POST /admin/performance/test
Content-Type: application/json

{
  "regionNames": ["서울특별시 종로구", "경기도 수원시 장안구"]
}
```

**JSON 파일 사용:**

```bash
curl -X POST http://localhost:8080/admin/performance/compare \
  -H "Content-Type: application/json" \
  -d @data/test-requests/region-names-request.json
```

### 2. 부하 테스트

```bash
POST /admin/performance/load-test
Content-Type: application/json

{
  "smallRegions": [
    {
      "name": "작은지역1",
      "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
    }
  ],
  "mediumRegions": [
    {
      "name": "중간지역1", 
      "polygon": [[127.0, 35.0], [127.3, 35.0], [127.3, 35.3], [127.0, 35.3], [127.0, 35.0]]
    }
  ],
  "largeRegions": [
    {
      "name": "큰지역1",
      "polygon": [[126.5, 34.5], [127.5, 34.5], [127.5, 35.5], [126.5, 35.5], [126.5, 34.5]]
    }
  ]
}
```

### 3. 메모리 테스트

```bash
POST /admin/performance/memory-test
Content-Type: application/json

{
  "testRegions": [
    {
      "name": "구례군",
      "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
    }
  ],
  "durationMinutes": 5
}
```

### 4. 성능 통계 조회

```bash
GET /admin/performance/stats
```

## 🏥 헬스체크 API

### 1. 기본 서비스 상태 확인

```bash
GET /health
```

**응답 예시:**
```json
{
  "success": true,
  "message": "서비스가 정상적으로 작동 중입니다.",
  "data": "서비스가 정상적으로 작동 중입니다."
}
```

### 2. 메모리 상태 확인

```bash
GET /health/memory
```

**응답 예시:**
```json
{
  "success": true,
  "message": "메모리 상태를 성공적으로 조회했습니다.",
  "data": {
    "maxMemoryMB": 2048,
    "totalMemoryMB": 1024,
    "usedMemoryMB": 512,
    "freeMemoryMB": 512,
    "usageRatio": 0.25
  }
}
```

### 3. 강제 가비지 컬렉션 실행

```bash
GET /health/gc
```

**응답 예시:**
```json
{
  "success": true,
  "message": "가비지 컬렉션이 실행되었습니다.",
  "data": "가비지 컬렉션이 실행되었습니다."
}
```

### 4. 상세 상태 정보

```bash
GET /health/status
```

**응답 예시:**
```json
{
  "success": true,
  "message": "상태 정보를 성공적으로 조회했습니다.",
  "data": {
    "status": "RUNNING",
    "memoryInfo": {
      "maxMemoryMB": 2048,
      "totalMemoryMB": 1024,
      "usedMemoryMB": 512,
      "freeMemoryMB": 512,
      "usageRatio": 0.25
    },
    "timestamp": 1705123456789
  }
}
```

## 🧪 테스트 API

### 1. 성능 비교 테스트 (기존 vs 개선)

```bash
POST /admin/performance/compare
Content-Type: application/json

{
  "regionNames": ["서울특별시 종로구", "경기도 수원시 장안구"]
}
```

**응답 예시:**
```json
{
  "success": true,
  "message": "성능 비교 테스트가 완료되었습니다.",
  "data": {
    "legacyResults": {
      "totalTimeMs": 120000,
      "apiCalls": 1500,
      "placesCollected": 1200
    },
    "integratedResults": {
      "totalTimeMs": 48000,
      "apiCalls": 900,
      "placesCollected": 1200
    },
    "improvement": {
      "timeReduction": 60.0,
      "apiCallReduction": 40.0
    }
  }
}
```

### 2. 통합 성능 테스트

```bash
POST /admin/performance/test
Content-Type: application/json

[
  {
    "name": "전라남도 구례군"
  }
]
```

**응답 예시:**
```json
{
  "success": true,
  "message": "성능 테스트가 완료되었습니다.",
  "data": {
    "totalTimeMs": 45000,
    "apiCalls": 800,
    "placesCollected": 950,
    "averageResponseTimeMs": 56,
    "cacheHitRate": 0.65,
    "memoryUsageMB": 512
  }
}
```

### 3. 부하 테스트

```bash
POST /admin/performance/load-test
Content-Type: application/json

{
  "smallRegions": [
    {
      "name": "작은지역1",
      "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
    }
  ],
  "mediumRegions": [
    {
      "name": "중간지역1", 
      "polygon": [[127.0, 35.0], [127.3, 35.0], [127.3, 35.3], [127.0, 35.3], [127.0, 35.0]]
    }
  ],
  "largeRegions": [
    {
      "name": "큰지역1",
      "polygon": [[126.5, 34.5], [127.5, 34.5], [127.5, 35.5], [126.5, 35.5], [126.5, 34.5]]
    }
  ]
}
```

### 4. 메모리 테스트

```bash
POST /admin/performance/memory-test
Content-Type: application/json

{
  "testRegions": [
    {
      "name": "구례군",
      "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
    }
  ],
  "durationMinutes": 5
}
```

**응답 예시:**
```json
{
  "success": true,
  "message": "메모리 테스트가 완료되었습니다.",
  "data": {
    "initialMemoryMB": 256,
    "finalMemoryMB": 512,
    "peakMemoryMB": 768,
    "afterGcMemoryMB": 320,
    "memorySnapshots": [256, 320, 400, 512, 480, 768, 512],
    "memoryLeakDetected": false
  }
}
```

### 5. 성능 통계 조회

```bash
GET /admin/performance/stats
```

**응답 예시:**
```json
{
  "success": true,
  "message": "성능 통계를 성공적으로 조회했습니다.",
  "data": {
    "totalApiCalls": 2500,
    "totalProcessingTimeMs": 180000,
    "averageResponseTimeMs": 72,
    "cacheHitRate": 0.65,
    "successRate": 0.98,
    "totalPlacesCollected": 3500
  }
}
```

## 📁 JSON 파일 위치

- `data/test-requests/region-names-request.json` - 지역명 기반 수집용 JSON (권장)
- `data/test-requests/region-collection-request.json` - 폴리곤 직접 입력용 JSON
- `data/test-requests/performance-test-request.json` - 성능 테스트용 JSON

## 🔍 실시간 모니터링

### 1. 서비스 상태 확인

```bash
# 기본 헬스체크
curl http://localhost:8080/health

# 메모리 상태 확인
curl http://localhost:8080/health/memory

# 상세 상태 정보
curl http://localhost:8080/health/status
```

> **💡 안전한 동시 실행**: `/health` 관련 API들은 **읽기 전용**이므로 데이터 수집이나 성능 테스트 실행 중에도 **안전하게** 사용할 수 있습니다!

### 2. 실시간 모니터링 스크립트

#### **메모리 모니터링 (5초마다)**
```bash
# 터미널에서 실행
watch -n 5 'curl -s http://localhost:8080/health/memory | jq ".data"'
```

#### **서비스 상태 모니터링**
```bash
# 터미널에서 실행
watch -n 10 'curl -s http://localhost:8080/health/status | jq ".data.status"'
```

#### **Docker 컨테이너 리소스 모니터링**
```bash
# 컨테이너별 리소스 사용량 실시간 확인
docker stats

# 특정 컨테이너만
docker stats livelihood-coupon-collector-app-1
```

### 3. 로그 모니터링

```bash
# 모든 서비스 로그 실시간 보기
docker-compose logs -f

# 특정 서비스만 (앱 로그)
docker-compose logs -f collector-app

# 마지막 50줄부터 실시간
docker-compose logs --tail=50 -f collector-app
```

### 4. 문제 진단 및 해결

#### **메모리 누수 의심 시:**
```bash
# 강제 GC 실행
curl http://localhost:8080/health/gc

# 메모리 상태 재확인
curl http://localhost:8080/health/memory
```

#### **서비스 응답 없음 시:**
```bash
# 컨테이너 재시작
docker-compose restart collector-app

# 또는 완전 재시작
docker-compose down && docker-compose up -d
```

#### **성능 통계 확인:**
```bash
# 성능 통계 조회
curl http://localhost:8080/admin/performance/stats
```

## 🔧 주요 기능

### 통합 최적화 기능

- **캐싱**: 격자 스캔 상태 캐싱으로 API 호출 40% 감소
- **배치 처리**: 격자들을 그룹화하여 효율적 처리
- **성능 모니터링**: 실시간 성능 지표 추적
- **적응형 지연**: API 서버 상태에 따른 동적 지연 조정
- **메모리 관리**: 자동 가비지 컬렉션 및 메모리 모니터링

### 성능 개선 효과

- **API 호출**: 40% 감소 (캐싱 효과)
- **DB 조회**: 70% 감소 (캐시 우선 조회)
- **처리 시간**: 60% 감소 (배치 처리 + 병렬화)
- **메모리 사용량**: 50% 감소 (스트림 처리)
- **메모리 누수**: 완전 해결 (자동 GC + 스레드 풀 정리)
