# 대회 시간 설정 API 명세서

## 목차
1. [개요](#개요)
2. [공통 사항](#공통-사항)
3. [API 목록](#api-목록)
   - [대회 시간 조회](#1-대회-시간-조회)
   - [대회 시간 설정](#2-대회-시간-설정-관리자)

---

## 개요

대회 시작/종료 시간을 DB에서 동적으로 관리하기 위한 API입니다.
관리자는 대회 시간을 설정할 수 있고, 모든 사용자는 현재 대회 시간을 조회할 수 있습니다.

### 주요 기능
- 대회 시작/종료 시간 조회
- 관리자의 대회 시간 설정 및 변경
- 현재 서버 시간과 함께 제공

---

## 공통 사항

### Base URL
```
https://api.example.com/api
```

### 인증
- 관리자 API는 JWT 토큰 필요
- `Authorization: Bearer {token}` 헤더 포함

### 응답 형식
- Content-Type: `application/json; charset=utf-8`
- 날짜 형식: `yyyy-MM-dd HH:mm:ss` (Asia/Seoul 타임존)

### 공통 에러 응답
```json
{
  "httpStatus": "NOT_FOUND",
  "description": "대회 설정을 찾을 수 없습니다."
}
```

---

## API 목록

### 1. 대회 시간 조회

대회의 시작/종료 시간과 현재 서버 시간을 조회합니다.

#### 기본 정보
- **URL**: `/contest-time`
- **Method**: `GET`
- **Auth**: 불필요 (모든 사용자 접근 가능)

#### Request

**Headers**
```
없음
```

**Parameters**
```
없음
```

#### Response

**Success Response (200 OK)**
```json
{
  "startTime": "2025-03-29 10:00:00",
  "endTime": "2025-03-29 22:00:00",
  "currentTime": "2025-10-13 15:30:45"
}
```

**Error Responses**

| Status Code | Description | Response Body |
|-------------|-------------|---------------|
| 404 | 대회 설정이 존재하지 않음 | `{"httpStatus": "NOT_FOUND", "description": "대회 설정을 찾을 수 없습니다."}` |

#### 예시

**cURL**
```bash
curl -X GET https://api.example.com/api/contest-time
```

**JavaScript (Fetch)**
```javascript
fetch('https://api.example.com/api/contest-time')
  .then(response => response.json())
  .then(data => {
    console.log('대회 시작:', data.startTime);
    console.log('대회 종료:', data.endTime);
    console.log('현재 시간:', data.currentTime);
  });
```

---

### 2. 대회 시간 설정 (관리자)

관리자가 대회의 시작/종료 시간을 설정하거나 변경합니다.

#### 기본 정보
- **URL**: `/admin/contest-time`
- **Method**: `PUT`
- **Auth**: 필요 (ADMIN 권한)

#### Request

**Headers**
```
Authorization: Bearer {access_token}
Content-Type: application/json
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

#### Response

**Success Response (200 OK)**
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

**Error Responses**

| Status Code | Description | Response Body |
|-------------|-------------|---------------|
| 401 | 인증되지 않음 | `{"httpStatus": "UNAUTHORIZED", "description": "인증이 필요합니다."}` |
| 403 | 권한 없음 | `{"httpStatus": "FORBIDDEN", "description": "권한이 없습니다."}` |
| 400 | 잘못된 요청 | `{"httpStatus": "BAD_REQUEST", "description": "잘못된 요청입니다."}` |

#### 참고 사항
- 새로운 설정이 생성되면 기존의 활성화된 설정은 자동으로 비활성화됩니다.
- 시작 시간은 종료 시간보다 이전이어야 합니다.
- 날짜 형식이 올바르지 않으면 400 에러가 발생합니다.

#### 예시

**cURL**
```bash
curl -X PUT https://api.example.com/api/admin/contest-time \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "startTime": "2025-03-29 10:00:00",
    "endTime": "2025-03-29 22:00:00"
  }'
```

**JavaScript (Fetch)**
```javascript
fetch('https://api.example.com/api/admin/contest-time', {
  method: 'PUT',
  headers: {
    'Authorization': 'Bearer YOUR_ACCESS_TOKEN',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    startTime: '2025-03-29 10:00:00',
    endTime: '2025-03-29 22:00:00'
  })
})
  .then(response => response.json())
  .then(data => {
    console.log('설정 완료:', data);
  });
```

**Python (requests)**
```python
import requests

url = "https://api.example.com/api/admin/contest-time"
headers = {
    "Authorization": "Bearer YOUR_ACCESS_TOKEN",
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

## DB 스키마

### contest_config 테이블

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

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| 1.0.0 | 2025-10-13 | 초기 API 명세 작성 |

---

## 문의

API 관련 문의사항은 아래로 연락 주시기 바랍니다.
- GitHub Issues: https://github.com/MJSEC-MJU/MSG_CTF_BACK/issues
