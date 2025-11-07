#!/usr/bin/env python3
"""
CTF 인덱스 성능 비교 테스트

테스트 시나리오:
1. 로그인 (UserEntity - login_id 인덱스 테스트)
2. 챌린지 제출 (HistoryEntity - 중복 체크 인덱스 테스트)
3. 리더보드 조회 (TeamEntity - 정렬 인덱스 테스트)

사용법:
    # 인덱스 추가 전 (베이스라인)
    python index_performance_test.py --mode baseline
    
    # 인덱스 추가 후 (비교)
    python index_performance_test.py --mode indexed
    
    # 결과 비교
    python index_performance_test.py --mode compare
"""

import requests
import json
import time
import concurrent.futures
from datetime import datetime
import argparse
from typing import List, Dict, Tuple
import statistics

# ==========================================
# 설정
# ==========================================

BASE_URL = "http://localhost:8080"
CONCURRENT_USERS = 100  # 동시 접속 사용자 수
TEST_CHALLENGE_ID = 1   # 테스트할 챌린지 ID

# ==========================================
# API 클라이언트
# ==========================================

class CTFClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json'
        })
    
    def login(self, login_id: str, password: str) -> Tuple[bool, float, str]:
        """
        로그인 성능 측정
        Returns: (성공여부, 소요시간(ms), 토큰)
        """
        url = f"{self.base_url}/api/users/sign-in"
        data = {
            "loginId": login_id,
            "password": password
        }
        
        start_time = time.time()
        try:
            response = self.session.post(url, json=data, timeout=30)
            elapsed = (time.time() - start_time) * 1000  # ms 변환
            
            if response.status_code == 200:
                result = response.json()
                token = result.get('accessToken', '')
                return True, elapsed, token
            else:
                return False, elapsed, ""
        except Exception as e:
            elapsed = (time.time() - start_time) * 1000
            return False, elapsed, ""
    
    def get_challenge(self, token: str, challenge_id: int) -> Tuple[bool, float]:
        """
        챌린지 조회 성능 측정 (내부적으로 중복 제출 체크 실행)
        Returns: (성공여부, 소요시간(ms))
        """
        url = f"{self.base_url}/api/challenges/{challenge_id}"
        headers = {"Authorization": f"Bearer {token}"}
        
        start_time = time.time()
        try:
            response = self.session.get(url, headers=headers, timeout=30)
            elapsed = (time.time() - start_time) * 1000
            
            return response.status_code == 200, elapsed
        except Exception as e:
            elapsed = (time.time() - start_time) * 1000
            return False, elapsed
    
    def get_leaderboard(self, token: str) -> Tuple[bool, float]:
        """
        리더보드 조회 성능 측정 (total_point, last_solved_time 정렬)
        Returns: (성공여부, 소요시간(ms))
        """
        url = f"{self.base_url}/api/teams/ranking"
        headers = {"Authorization": f"Bearer {token}"}
        
        start_time = time.time()
        try:
            response = self.session.get(url, headers=headers, timeout=30)
            elapsed = (time.time() - start_time) * 1000
            
            return response.status_code == 200, elapsed
        except Exception as e:
            elapsed = (time.time() - start_time) * 1000
            return False, elapsed

# ==========================================
# 테스트 시나리오
# ==========================================

def load_test_users(csv_path: str = "generated_passwords.csv") -> List[Dict]:
    """생성된 사용자 정보 로드"""
    users = []
    
    # 방법 1: test_data.json 읽기 시도 (ctf_test.py로 생성)
    try:
        with open('test_data.json', 'r', encoding='utf-8') as f:
            test_data = json.load(f)
            users = test_data.get('users', [])
            if users:
                print(f"✅ test_data.json에서 {len(users)}명 로드")
                return users
    except FileNotFoundError:
        pass
    
    # 방법 2: CSV 파일 읽기 시도 (add_team.py로 생성)
    try:
        with open(csv_path, 'r', encoding='utf-8-sig') as f:
            lines = f.readlines()
            # 헤더 스킵
            for line in lines[1:]:
                parts = line.strip().split(',')
                if len(parts) >= 4:
                    users.append({
                        'loginId': parts[2],
                        'password': parts[3]
                    })
        
        if users:
            print(f"✅ {csv_path}에서 {len(users)}명 로드")
            return users
    except FileNotFoundError:
        pass
    
    # 둘 다 없으면 에러
    print(f"❌ 오류: 테스트 사용자 정보를 찾을 수 없습니다.")
    print(f"   다음 중 하나를 먼저 실행해주세요:")
    print(f"   1) python ctf_test.py --mode setup")
    print(f"   2) python add_team.py ... --export-passwords {csv_path}")
    return []

def test_single_user(user: Dict, client: CTFClient) -> Dict:
    """단일 사용자의 전체 플로우 테스트"""
    result = {
        'loginId': user['loginId'],
        'login_success': False,
        'login_time': 0,
        'challenge_success': False,
        'challenge_time': 0,
        'leaderboard_success': False,
        'leaderboard_time': 0,
        'total_time': 0
    }
    
    start_total = time.time()
    
    # 1. 로그인 테스트
    success, login_time, token = client.login(user['loginId'], user['password'])
    result['login_success'] = success
    result['login_time'] = login_time
    
    if not success:
        result['total_time'] = (time.time() - start_total) * 1000
        return result
    
    # 2. 챌린지 조회 테스트 (중복 체크 포함)
    success, challenge_time = client.get_challenge(token, TEST_CHALLENGE_ID)
    result['challenge_success'] = success
    result['challenge_time'] = challenge_time
    
    # 3. 리더보드 조회 테스트
    success, leaderboard_time = client.get_leaderboard(token)
    result['leaderboard_success'] = success
    result['leaderboard_time'] = leaderboard_time
    
    result['total_time'] = (time.time() - start_total) * 1000
    
    return result

def run_concurrent_test(users: List[Dict], concurrent_count: int = 100) -> List[Dict]:
    """동시 접속 테스트 실행"""
    print(f"\n{'='*80}")
    print(f"🚀 동시 접속 테스트 시작: {concurrent_count}명")
    print(f"{'='*80}\n")
    
    # 테스트할 사용자 선택
    test_users = users[:concurrent_count]
    
    print(f"[1/3] 사용자 준비: {len(test_users)}명")
    print(f"[2/3] 테스트 시작 대기...")
    time.sleep(3)
    
    print(f"[3/3] 🏃 테스트 실행 중...\n")
    
    results = []
    start_time = time.time()
    
    # 동시 실행
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrent_count) as executor:
        client = CTFClient(BASE_URL)
        futures = [executor.submit(test_single_user, user, client) for user in test_users]
        
        for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
            try:
                result = future.result()
                results.append(result)
                
                # 진행 상황 표시
                status = "✓" if result['login_success'] else "✗"
                print(f"  {status} {result['loginId']:20s} | "
                      f"로그인: {result['login_time']:6.0f}ms | "
                      f"챌린지: {result['challenge_time']:6.0f}ms | "
                      f"리더보드: {result['leaderboard_time']:6.0f}ms | "
                      f"합계: {result['total_time']:6.0f}ms")
                
            except Exception as e:
                print(f"  ✗ 오류 발생: {e}")
    
    total_time = time.time() - start_time
    
    print(f"\n{'='*80}")
    print(f"총 소요 시간: {total_time:.2f}초")
    print(f"{'='*80}\n")
    
    return results

# ==========================================
# 결과 분석
# ==========================================

def analyze_results(results: List[Dict], mode: str):
    """결과 통계 분석 및 출력"""
    if not results:
        print("❌ 분석할 결과가 없습니다.")
        return
    
    # 성공한 요청만 필터링
    successful = [r for r in results if r['login_success']]
    
    if not successful:
        print("❌ 성공한 요청이 없습니다.")
        return
    
    # 통계 계산
    login_times = [r['login_time'] for r in successful]
    challenge_times = [r['challenge_time'] for r in successful]
    leaderboard_times = [r['leaderboard_time'] for r in successful]
    total_times = [r['total_time'] for r in successful]
    
    stats = {
        'mode': mode,
        'timestamp': datetime.now().isoformat(),
        'total_requests': len(results),
        'successful_requests': len(successful),
        'success_rate': len(successful) / len(results) * 100,
        'login': {
            'min': min(login_times),
            'max': max(login_times),
            'avg': statistics.mean(login_times),
            'median': statistics.median(login_times),
            'p95': sorted(login_times)[int(len(login_times) * 0.95)]
        },
        'challenge': {
            'min': min(challenge_times),
            'max': max(challenge_times),
            'avg': statistics.mean(challenge_times),
            'median': statistics.median(challenge_times),
            'p95': sorted(challenge_times)[int(len(challenge_times) * 0.95)]
        },
        'leaderboard': {
            'min': min(leaderboard_times),
            'max': max(leaderboard_times),
            'avg': statistics.mean(leaderboard_times),
            'median': statistics.median(leaderboard_times),
            'p95': sorted(leaderboard_times)[int(len(leaderboard_times) * 0.95)]
        },
        'total': {
            'min': min(total_times),
            'max': max(total_times),
            'avg': statistics.mean(total_times),
            'median': statistics.median(total_times),
            'p95': sorted(total_times)[int(len(total_times) * 0.95)]
        }
    }
    
    # 결과 출력
    print(f"\n{'='*80}")
    print(f"📊 성능 분석 결과 [{mode.upper()}]")
    print(f"{'='*80}\n")
    
    print(f"✅ 성공률: {stats['success_rate']:.1f}% ({stats['successful_requests']}/{stats['total_requests']})\n")
    
    print("┌─────────────────┬──────────┬──────────┬──────────┬──────────┬──────────┐")
    print("│ 측정 항목       │   최소   │   평균   │   중앙값 │   95%    │   최대   │")
    print("├─────────────────┼──────────┼──────────┼──────────┼──────────┼──────────┤")
    
    for name, key in [('로그인', 'login'), ('챌린지 조회', 'challenge'), 
                      ('리더보드', 'leaderboard'), ('전체', 'total')]:
        s = stats[key]
        print(f"│ {name:13s}   │ {s['min']:6.0f}ms │ {s['avg']:6.0f}ms │ "
              f"{s['median']:6.0f}ms │ {s['p95']:6.0f}ms │ {s['max']:6.0f}ms │")
    
    print("└─────────────────┴──────────┴──────────┴──────────┴──────────┴──────────┘\n")
    
    # 파일로 저장
    filename = f"performance_{mode}.json"
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    
    print(f"📄 결과 저장: {filename}\n")
    
    return stats

def compare_results():
    """baseline과 indexed 결과 비교"""
    try:
        with open('performance_baseline.json', 'r') as f:
            baseline = json.load(f)
        with open('performance_indexed.json', 'r') as f:
            indexed = json.load(f)
    except FileNotFoundError as e:
        print(f"❌ 오류: {e}")
        print("   baseline과 indexed 테스트를 먼저 실행해주세요.")
        return
    
    print(f"\n{'='*80}")
    print(f"📊 인덱스 성능 비교 분석")
    print(f"{'='*80}\n")
    
    print("┌─────────────────┬──────────────────┬──────────────────┬──────────────────┐")
    print("│ 측정 항목       │  인덱스 전 (ms) │  인덱스 후 (ms) │   개선율         │")
    print("├─────────────────┼──────────────────┼──────────────────┼──────────────────┤")
    
    for name, key in [('로그인 (평균)', 'login'), ('챌린지 (평균)', 'challenge'), 
                      ('리더보드 (평균)', 'leaderboard'), ('전체 (평균)', 'total')]:
        before = baseline[key]['avg']
        after = indexed[key]['avg']
        improvement = ((before - after) / before) * 100
        
        arrow = "🚀" if improvement > 0 else "⚠️"
        print(f"│ {name:13s}   │ {before:14.0f}ms │ {after:14.0f}ms │ "
              f"{arrow} {improvement:+6.1f}%      │")
    
    print("└─────────────────┴──────────────────┴──────────────────┴──────────────────┘\n")
    
    # P95 비교
    print("┌─────────────────┬──────────────────┬──────────────────┬──────────────────┐")
    print("│ 측정 항목 (P95)│  인덱스 전 (ms) │  인덱스 후 (ms) │   개선율         │")
    print("├─────────────────┼──────────────────┼──────────────────┼──────────────────┤")
    
    for name, key in [('로그인', 'login'), ('챌린지', 'challenge'), 
                      ('리더보드', 'leaderboard'), ('전체', 'total')]:
        before = baseline[key]['p95']
        after = indexed[key]['p95']
        improvement = ((before - after) / before) * 100
        
        arrow = "🚀" if improvement > 0 else "⚠️"
        print(f"│ {name:13s}   │ {before:14.0f}ms │ {after:14.0f}ms │ "
              f"{arrow} {improvement:+6.1f}%      │")
    
    print("└─────────────────┴──────────────────┴──────────────────┴──────────────────┘\n")
    
    # 종합 평가
    total_improvement = ((baseline['total']['avg'] - indexed['total']['avg']) / baseline['total']['avg']) * 100
    
    print("┌────────────────────────────────────────────────────────────────────────────┐")
    print("│ 📈 종합 평가                                                                │")
    print("├────────────────────────────────────────────────────────────────────────────┤")
    
    if total_improvement > 50:
        print(f"│ 🎉 대폭 개선! 전체 응답 시간이 {total_improvement:.1f}% 빨라졌습니다.                  │")
    elif total_improvement > 20:
        print(f"│ ✅ 상당한 개선! 전체 응답 시간이 {total_improvement:.1f}% 빨라졌습니다.                │")
    elif total_improvement > 0:
        print(f"│ 👍 개선됨. 전체 응답 시간이 {total_improvement:.1f}% 빨라졌습니다.                    │")
    else:
        print(f"│ ⚠️  개선되지 않음. 인덱스 설정을 확인해주세요.                              │")
    
    print("└────────────────────────────────────────────────────────────────────────────┘\n")

# ==========================================
# 메인
# ==========================================

def main():
    parser = argparse.ArgumentParser(description='CTF 인덱스 성능 비교 테스트')
    parser.add_argument('--mode', choices=['baseline', 'indexed', 'compare'], required=True,
                        help='baseline: 인덱스 전 | indexed: 인덱스 후 | compare: 결과 비교')
    parser.add_argument('--users', type=int, default=CONCURRENT_USERS,
                        help=f'동시 접속 사용자 수 (기본값: {CONCURRENT_USERS})')
    parser.add_argument('--csv', default='generated_passwords.csv',
                        help='사용자 정보 CSV 파일 경로')
    
    args = parser.parse_args()
    
    print("="*80)
    print("🎯 CTF 인덱스 성능 비교 테스트")
    print("="*80)
    print(f"서버: {BASE_URL}")
    print(f"모드: {args.mode.upper()}")
    print(f"동시 사용자: {args.users}명")
    print("="*80)
    
    if args.mode == 'compare':
        compare_results()
    else:
        # 사용자 정보 로드
        users = load_test_users(args.csv)
        if not users:
            return
        
        print(f"\n✅ 사용자 {len(users)}명 로드 완료")
        
        if len(users) < args.users:
            print(f"⚠️  경고: 요청한 {args.users}명보다 적은 {len(users)}명만 사용 가능")
            args.users = len(users)
        
        # 테스트 실행
        results = run_concurrent_test(users, args.users)
        
        # 결과 분석
        analyze_results(results, args.mode)
    
    print("="*80)
    print("✅ 완료!")
    print("="*80)

if __name__ == "__main__":
    main()