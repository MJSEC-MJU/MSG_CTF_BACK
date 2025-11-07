#!/usr/bin/env python3
"""
CTF 플랫폼 동시성 테스트 스크립트 (최종 수정 버전)

수정 사항:
- login() 메서드의 accessToken 추출 로직 수정
- 응답 구조에 맞게 최상위에서 accessToken 가져오기

사용법:
    python ctf_test_final.py --mode setup     # 테스트 데이터 생성
    python ctf_test_final.py --mode test      # 동시성 테스트 실행
    python ctf_test_final.py --mode all       # 전체 실행
"""

import requests
import json
import time
import concurrent.futures
from datetime import datetime, timedelta
import argparse
from typing import List, Dict, Tuple

# ==========================================
# 설정
# ==========================================

BASE_URL = "http://localhost:8080"
ADMIN_LOGIN_ID = "admin"
ADMIN_PASSWORD = "1234"

TEST_USERS_COUNT = 100
TEST_TEAMS_COUNT = 100
CONCURRENT_REQUESTS = 100

# ==========================================
# API 클라이언트
# ==========================================

class CTFClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.token = None
        self.session = requests.Session()
    
    def login(self, login_id: str, password: str) -> str:
        """로그인하고 토큰 반환"""
        url = f"{self.base_url}/api/users/sign-in"
        data = {
            "loginId": login_id,
            "password": password
        }
        
        print(f"      [DEBUG] 로그인 시도: {login_id}")
        
        try:
            response = self.session.post(url, json=data)
            
            print(f"      [DEBUG] 응답 상태 코드: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                print(f"      [DEBUG] JSON 파싱 성공")
                
                # ✅ 수정: accessToken은 최상위에 있음!
                if 'accessToken' in result:
                    self.token = result['accessToken']
                    print(f"      [DEBUG] ✅ accessToken 추출 성공 (최상위)")
                elif 'data' in result and isinstance(result['data'], dict) and 'accessToken' in result['data']:
                    self.token = result['data']['accessToken']
                    print(f"      [DEBUG] ✅ accessToken 추출 성공 (data 필드)")
                else:
                    print(f"      [ERROR] accessToken을 찾을 수 없습니다!")
                    print(f"      [ERROR] 응답: {result}")
                    raise Exception(f"accessToken을 찾을 수 없습니다.")
                
                # 토큰 검증
                if not self.token or len(self.token) < 10:
                    raise Exception(f"토큰이 유효하지 않습니다: {self.token}")
                
                if self.token.count('.') != 2:
                    print(f"      [WARNING] JWT 형식이 이상합니다 (점이 2개가 아님)")
                
                print(f"      [DEBUG] 토큰 길이: {len(self.token)}자")
                print(f"      [DEBUG] 토큰 (처음 50자): {self.token[:50]}...")
                
                return self.token
            else:
                raise Exception(f"로그인 실패 (Status {response.status_code}): {response.text}")
                
        except requests.exceptions.RequestException as e:
            print(f"      [ERROR] 네트워크 오류: {e}")
            raise
        except json.JSONDecodeError as e:
            print(f"      [ERROR] JSON 파싱 실패: {e}")
            raise
    
    def admin_create_user(self, login_id: str, password: str, email: str, name: str, univ: str, role: str = "USER") -> bool:
        """관리자 권한으로 사용자 생성"""
        url = f"{self.base_url}/api/admin/add/member"
        
        if not self.token:
            print(f"      [ERROR] 토큰이 없습니다!")
            return False
        
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }
        data = {
            "loginId": login_id,
            "password": password,
            "email": email,
            "name": name,
            "univ": univ,
            "role": role
        }
        
        response = self.session.post(url, json=data, headers=headers)
        
        if response.status_code != 201:
            print(f"      [DEBUG] 사용자 생성 실패 - Status: {response.status_code}")
            print(f"      [DEBUG] 응답: {response.text[:200]}")
        
        return response.status_code == 201
    
    def create_team(self, team_name: str) -> bool:
        """팀 생성"""
        url = f"{self.base_url}/api/admin/team/create"
        
        if not self.token:
            print(f"      [ERROR] 토큰이 없습니다!")
            return False
        
        headers = {"Authorization": f"Bearer {self.token}"}
        params = {"teamName": team_name}
        
        response = self.session.post(url, params=params, headers=headers)
        
        if response.status_code != 200:
            print(f"      [DEBUG] 팀 생성 실패 - Status: {response.status_code}")
        
        return response.status_code == 200
    
    def add_member_to_team(self, team_name: str, email: str) -> bool:
        """팀에 멤버 추가"""
        url = f"{self.base_url}/api/admin/team/member/{team_name}"
        
        if not self.token:
            print(f"      [ERROR] 토큰이 없습니다!")
            return False
        
        headers = {"Authorization": f"Bearer {self.token}"}
        params = {"email": email}
        
        response = self.session.post(url, params=params, headers=headers)
        
        if response.status_code != 200:
            print(f"      [DEBUG] 팀 멤버 추가 실패 - Status: {response.status_code}")
            print(f"      [DEBUG] 응답: {response.text[:200]}")
        
        return response.status_code == 200
    
    def create_challenge(self, title: str, flag: str, points: int = 500) -> Dict:
        """문제 생성"""
        url = f"{self.base_url}/api/admin/create/challenge-no-file"
        
        if not self.token:
            raise Exception("토큰이 없습니다!")
        
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }
        
        # Java LocalDateTime이 파싱 가능한 형식으로 변환 (밀리초 제거)
        # "2025-10-30 14:37:45" 형식 (초까지만)
        now = datetime.now()
        start_time = now.strftime("%Y-%m-%d %H:%M:%S")  # 밀리초 없이
        
        future = now + timedelta(days=7)
        end_time = future.strftime("%Y-%m-%d %H:%M:%S")
        
        data = {
            "title": title,
            "description": "Test challenge for concurrency testing",
            "flag": flag,
            "points": points,
            "minPoints": 100,
            "initialPoints": points,
            "startTime": start_time,
            "endTime": end_time,
            "category": "MISC",
            "mileage": 100
        }
        
        response = self.session.post(url, json=data, headers=headers)
        
        if response.status_code == 200:
            print(f"      ✅ 문제 생성 성공")
            return {"data": None}
        else:
            print(f"      [DEBUG] 문제 생성 실패 - Status: {response.status_code}")
            raise Exception(f"Challenge creation failed: {response.text}")
    
    def submit_flag(self, challenge_id: int, flag: str) -> Tuple[str, float]:
        """플래그 제출"""
        url = f"{self.base_url}/api/challenges/{challenge_id}/submit"
        
        if not self.token:
            return "Error: No token", 0.0
        
        headers = {"Authorization": f"Bearer {self.token}"}
        data = {"submitFlag": flag}
        
        start_time = time.time()
        response = self.session.post(url, json=data, headers=headers)
        duration = (time.time() - start_time) * 1000
        
        if response.status_code == 200:
            result = response.json().get('data', 'Unknown')
            return result, duration
        else:
            return f"Error: {response.status_code}", duration

# ==========================================
# 테스트 데이터 생성
# ==========================================

def setup_test_data(client: CTFClient):
    """테스트 데이터 생성"""
    print("🔧 테스트 데이터 생성 시작...")
    
    # 0. 관리자 로그인
    print(f"\n0️⃣ 관리자 로그인 중...")
    print(f"   ID: {ADMIN_LOGIN_ID}")
    
    try:
        token = client.login(ADMIN_LOGIN_ID, ADMIN_PASSWORD)
        print(f"   ✅ 로그인 성공 (토큰 길이: {len(token)}자)")
    except Exception as e:
        print(f"   ❌ 로그인 실패: {e}")
        return None
    
    # 1. 테스트 사용자 생성
    print(f"\n1️⃣ {TEST_USERS_COUNT}명의 테스트 사용자 생성 중...")
    users = []
    
    for i in range(1, TEST_USERS_COUNT + 1):
        login_id = f"testuser{i}"
        password = "Test1234@"
        email = f"testuser{i}@mju.ac.kr"
        name = f"테스트유저{i}"
        univ = "명지대학교"
        
        try:
            if client.admin_create_user(login_id, password, email, name, univ):
                users.append({
                    "loginId": login_id,
                    "password": password
                })
                if i % 10 == 0:
                    print(f"   ✅ {i}/{TEST_USERS_COUNT} 생성 완료...")
        except Exception as e:
            print(f"   ❌ {login_id} 생성 오류: {e}")
        
        time.sleep(0.05)
    
    print(f"   ✅ 총 {len(users)}명 생성 완료")
    
    # 2. 테스트 팀 생성
    print(f"\n2️⃣ {TEST_TEAMS_COUNT}개의 테스트 팀 생성 중...")
    teams = []
    
    for i in range(1, TEST_TEAMS_COUNT + 1):
        team_name = f"TestTeam{i}"
        
        try:
            if client.create_team(team_name):
                teams.append(team_name)
                if i % 10 == 0:
                    print(f"   ✅ {i}/{TEST_TEAMS_COUNT} 팀 생성...")
        except Exception as e:
            print(f"   ❌ {team_name} 생성 오류: {e}")
        
        time.sleep(0.05)
    
    print(f"   ✅ 총 {len(teams)}개 팀 생성 완료")
    
    # 2-1. 사용자를 팀에 배정
    print(f"\n2-1️⃣ 사용자를 팀에 배정 중...")
    print(f"   ℹ️  각 사용자를 각 팀에 1:1로 배정합니다.")
    
    assigned_count = 0
    for i in range(min(len(users), len(teams))):
        user = users[i]
        team_name = teams[i]
        
        try:
            # testuser1 -> TestTeam1, testuser2 -> TestTeam2, ...
            if client.add_member_to_team(team_name, user['loginId'] + '@mju.ac.kr'):
                assigned_count += 1
                if (i + 1) % 10 == 0:
                    print(f"   ✅ {i + 1}명 배정 완료...")
        except Exception as e:
            print(f"   ❌ {user['loginId']} -> {team_name} 배정 오류: {e}")
        
        time.sleep(0.05)
    
    print(f"   ✅ 총 {assigned_count}명 팀 배정 완료")
    
    # 3. 테스트 문제 생성
    print(f"\n3️⃣ 테스트 문제 생성 중...")
    
    try:
        client.create_challenge(
            title="동시성 테스트 문제",
            flag="FLAG{test_concurrency_2025}",
            points=500
        )
        
        print(f"\n   💡 생성된 문제 ID를 확인하세요:")
        print(f"      - 관리자 페이지")
        print(f"      - DB: SELECT * FROM challenge ORDER BY challenge_id DESC LIMIT 1;")
        
        challenge_id_input = input("\n   문제 ID 입력 (엔터 시 1): ").strip()
        challenge_id = int(challenge_id_input) if challenge_id_input else 1
        
        print(f"   ✅ 문제 ID: {challenge_id}로 설정")
        
        # 데이터 저장
        test_data = {
            "users": users,
            "teams": teams,
            "challenge": {
                "id": challenge_id,
                "flag": "FLAG{test_concurrency_2025}"
            }
        }
        
        with open("test_data.json", "w", encoding="utf-8") as f:
            json.dump(test_data, f, ensure_ascii=False, indent=2)
        
        print(f"\n✅ 테스트 데이터 생성 완료!")
        print(f"   - 사용자: {len(users)}명")
        print(f"   - 팀: {len(teams)}개")
        print(f"   - 문제 ID: {challenge_id}")
        print(f"   - 저장 위치: test_data.json")
        
        return test_data
        
    except Exception as e:
        print(f"   ❌ 문제 생성 오류: {e}")
        return None

# ==========================================
# 동시성 테스트
# ==========================================

def run_concurrent_test(client: CTFClient):
    """동시성 테스트 실행"""
    print("\n🚀 동시성 테스트 시작...")
    
    try:
        with open("test_data.json", "r", encoding="utf-8") as f:
            test_data = json.load(f)
    except FileNotFoundError:
        print("❌ test_data.json이 없습니다. --mode setup을 먼저 실행하세요.")
        return
    
    users = test_data['users']
    challenge_id = test_data['challenge']['id']
    flag = test_data['challenge']['flag']
    
    print(f"   문제 ID: {challenge_id}")
    print(f"   플래그: {flag}")
    print(f"   동시 요청 수: {CONCURRENT_REQUESTS}")
    
    # 사용자 로그인
    print(f"\n🔐 사용자 로그인 중...")
    clients = []
    
    for i in range(min(CONCURRENT_REQUESTS, len(users))):
        user = users[i]
        user_client = CTFClient(BASE_URL)
        
        try:
            user_client.login(user['loginId'], user['password'])
            clients.append((user['loginId'], user_client))
            if (i + 1) % 10 == 0:
                print(f"   ✅ {i + 1}명 로그인...")
        except Exception as e:
            print(f"   ⚠️  {user['loginId']} 로그인 실패")
    
    print(f"   ✅ 총 {len(clients)}명 로그인 완료")
    
    # 동시 제출
    print(f"\n⚡ {len(clients)}개 동시 제출 실행 중...")
    start_time = time.time()
    
    def submit_task(user_info):
        login_id, user_client = user_info
        try:
            result, duration = user_client.submit_flag(challenge_id, flag)
            return {
                "loginId": login_id,
                "result": result,
                "duration": duration,
                "success": True
            }
        except Exception as e:
            return {
                "loginId": login_id,
                "result": str(e),
                "duration": 0,
                "success": False
            }
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as executor:
        futures = [executor.submit(submit_task, info) for info in clients]
        results = [f.result() for f in concurrent.futures.as_completed(futures)]
    
    total_time = time.time() - start_time
    
    # 결과 분석
    print(f"\n📊 테스트 결과")
    print("="*60)
    
    correct = sum(1 for r in results if r['result'] == 'Correct')
    submitted = sum(1 for r in results if r['result'] == 'Submitted')
    wait = sum(1 for r in results if r['result'] == 'Wait')
    wrong = sum(1 for r in results if r['result'] == 'Wrong')
    error = sum(1 for r in results if 'Error' in str(r['result']))
    
    print(f"\n📈 응답 결과:")
    print(f"   ✅ Correct:   {correct:3d}개")
    print(f"   ℹ️  Submitted: {submitted:3d}개")
    print(f"   ⏸️  Wait:      {wait:3d}개")
    print(f"   ❌ Wrong:     {wrong:3d}개")
    print(f"   ⚠️  Error:    {error:3d}개")
    
    durations = [r['duration'] for r in results if r['success'] and r['duration'] > 0]
    
    if durations:
        sorted_d = sorted(durations)
        print(f"\n⏱️  응답 시간:")
        print(f"   평균: {sum(durations)/len(durations):.1f}ms")
        print(f"   최소: {min(durations):.1f}ms")
        print(f"   최대: {max(durations):.1f}ms")
        print(f"   P50:  {sorted_d[len(sorted_d)//2]:.1f}ms")
        print(f"   P95:  {sorted_d[int(len(sorted_d)*0.95)]:.1f}ms")
    
    print(f"\n🎯 전체 시간: {total_time:.2f}초")
    
    success_rate = ((correct + submitted) / len(results)) * 100 if results else 0
    print(f"\n{'='*60}")
    
    if success_rate >= 95:
        print(f"✅ 테스트 성공! (성공률: {success_rate:.1f}%)")
    elif success_rate >= 80:
        print(f"⚠️  부분 성공 (성공률: {success_rate:.1f}%)")
    else:
        print(f"❌ 테스트 실패 (성공률: {success_rate:.1f}%)")
    
    # 결과 저장
    with open("test_results.json", "w", encoding="utf-8") as f:
        json.dump({
            "timestamp": datetime.now().isoformat(),
            "total_requests": len(results),
            "total_time": total_time,
            "success_rate": success_rate,
            "summary": {
                "correct": correct,
                "submitted": submitted,
                "wait": wait,
                "wrong": wrong,
                "error": error
            },
            "details": results
        }, f, ensure_ascii=False, indent=2)
    
    print(f"\n📄 결과 저장: test_results.json")

# ==========================================
# 메인
# ==========================================

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=['setup', 'test', 'all'], default='all')
    args = parser.parse_args()
    
    client = CTFClient(BASE_URL)
    
    print("="*60)
    print("🎯 CTF 동시성 테스트 v2.2 (최종)")
    print("="*60)
    print(f"서버: {BASE_URL}")
    print(f"관리자: {ADMIN_LOGIN_ID}")
    print("="*60)
    
    if args.mode in ['setup', 'all']:
        setup_test_data(client)
    
    if args.mode in ['test', 'all']:
        if args.mode == 'all':
            print("\n⏳ 5초 대기...")
            time.sleep(5)
        run_concurrent_test(client)
    
    print("\n" + "="*60)
    print("✅ 완료!")
    print("="*60)

if __name__ == "__main__":
    main()