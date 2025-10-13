# 대회 시간 설정 API 명세서

## 1. 대회 시간 조회

대회의 시작/종료 시간과 현재 서버 시간을 조회합니다.

### Endpoint

```
GET /api/contest-time
```

### Request

**Headers**
```json
{
  "Content-Type": "application/json"
}
```

**Body**
```
없음
```

### Query Params

없음

### Response 200

```json
{
  "startTime": "2025-03-29 10:00:00",
  "endTime": "2025-03-29 22:00:00",
  "currentTime": "2025-10-13 15:30:45"
}
```

| Field | Type | Description |
|-------|------|-------------|
| startTime | String | 대회 시작 시간 (yyyy-MM-dd HH:mm:ss) |
| endTime | String | 대회 종료 시간 (yyyy-MM-dd HH:mm:ss) |
| currentTime | String | 현재 서버 시간 (yyyy-MM-dd HH:mm:ss) |

### Response 404

- 대회 설정을 찾을 수 없음

```json
{
  "httpStatus": "NOT_FOUND",
  "description": "대회 설정을 찾을 수 없습니다."
}
```

### Response 500

- 서버 에러

---

## 2. 대회 시간 설정 (관리자)

관리자가 대회의 시작/종료 시간을 설정하거나 변경합니다.

### Endpoint

```
PUT /api/admin/contest-time
```

### Request

**Headers**
```json
{
  "Content-Type": "application/json",
  "Authorization": "Bearer <JWT_TOKEN>"
}
```

**Body**
```json
{
  "startTime": "2025-03-29 10:00:00",
  "endTime": "2025-03-29 22:00:00"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| startTime | String | Yes | 대회 시작 시간 (yyyy-MM-dd HH:mm:ss) |
| endTime | String | Yes | 대회 종료 시간 (yyyy-MM-dd HH:mm:ss) |

### Query Params

없음

### Response 200

```json
{
  "message": "대회 시간 설정 성공",
  "data": {
    "id": 1,
    "startTime": "2025-03-29 10:00:00",
    "endTime": "2025-03-29 22:00:00",
    "isActive": true
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| message | String | 응답 메시지 |
| data.id | Integer | 대회 설정 ID |
| data.startTime | String | 대회 시작 시간 (yyyy-MM-dd HH:mm:ss) |
| data.endTime | String | 대회 종료 시간 (yyyy-MM-dd HH:mm:ss) |
| data.isActive | Boolean | 활성화 여부 |

### Response 400

- 유효하지 않은 body 데이터

```json
{
  "httpStatus": "BAD_REQUEST",
  "description": "잘못된 요청입니다."
}
```

### Response 401

- 인증되지 않음 (JWT 토큰 없음 또는 만료)

```json
{
  "httpStatus": "UNAUTHORIZED",
  "description": "인증이 필요합니다."
}
```

### Response 403

- 권한 없음 (관리자 권한 필요)

```json
{
  "httpStatus": "FORBIDDEN",
  "description": "권한이 없습니다."
}
```

### Response 500

- 서버 에러

---

## 참고 사항

### 타임존
- 모든 시간은 **Asia/Seoul (KST, UTC+09:00)** 타임존 기준입니다.

### 날짜 형식
- 날짜 형식: `yyyy-MM-dd HH:mm:ss`
- 예시: `2025-03-29 10:00:00`

### 대회 시간 설정 규칙
1. 새로운 설정이 생성되면 기존의 활성화된 설정은 자동으로 비활성화됩니다.
2. 시작 시간은 종료 시간보다 이전이어야 합니다.
3. 날짜 형식이 올바르지 않으면 400 에러가 발생합니다.
4. 관리자 권한(ROLE_ADMIN)이 필요합니다.

### DB 테이블: contest_config

| Column | Type | Null | Key | Description |
|--------|------|------|-----|-------------|
| id | BIGINT | NO | PRI | 기본 키 (자동 증가) |
| start_time | TIMESTAMP | YES | | 대회 시작 시간 |
| end_time | TIMESTAMP | YES | | 대회 종료 시간 |
| is_active | BOOLEAN | YES | | 활성화 여부 |
| created_at | TIMESTAMP | YES | | 생성 시간 |
| updated_at | TIMESTAMP | YES | | 수정 시간 |
| deleted_at | TIMESTAMP | YES | | 삭제 시간 |

---

## 예시 코드

### cURL

**대회 시간 조회**
```bash
curl -X GET https://api.example.com/api/contest-time
```

**대회 시간 설정 (관리자)**
```bash
curl -X PUT https://api.example.com/api/admin/contest-time \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "startTime": "2025-03-29 10:00:00",
    "endTime": "2025-03-29 22:00:00"
  }'
```

### JavaScript (Fetch)

**대회 시간 조회**
```javascript
fetch('https://api.example.com/api/contest-time')
  .then(response => response.json())
  .then(data => {
    console.log('대회 시작:', data.startTime);
    console.log('대회 종료:', data.endTime);
    console.log('현재 시간:', data.currentTime);
  });
```

**대회 시간 설정 (관리자)**
```javascript
fetch('https://api.example.com/api/admin/contest-time', {
  method: 'PUT',
  headers: {
    'Authorization': 'Bearer YOUR_JWT_TOKEN',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    startTime: '2025-03-29 10:00:00',
    endTime: '2025-03-29 22:00:00'
  })
})
  .then(response => response.json())
  .then(data => {
    console.log('설정 완료:', data.message);
  });
```

### Python (requests)

**대회 시간 조회**
```python
import requests

url = "https://api.example.com/api/contest-time"
response = requests.get(url)
print(response.json())
```

**대회 시간 설정 (관리자)**
```python
import requests

url = "https://api.example.com/api/admin/contest-time"
headers = {
    "Authorization": "Bearer YOUR_JWT_TOKEN",
    "Content-Type": "application/json"
}
data = {
    "startTime": "2025-03-29 10:00:00",
    "endTime": "2025-03-29 22:00:00"
}

response = requests.put(url, headers=headers, json=data)
print(response.json())
```

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| 1.0.0 | 2025-10-13 | 초기 API 명세 작성 |
