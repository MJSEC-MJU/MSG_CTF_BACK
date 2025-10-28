"""
CTF IP 차단 시스템 테스트 스크립트

테스트 가능한 공격 유형:
1. SQL Injection
2. XSS Attack
3. Path Traversal
4. Brute Force (Flag)
5. Brute Force (Login)
6. Rate Limiting
"""

import requests
import time
from typing import Optional

BASE_URL = "http://localhost:8080"
ADMIN_USER = {
    "loginId": "admin",
    "password": "admin1234!"
}

class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def print_header(text: str):
    print(f"\n{Colors.HEADER}{Colors.BOLD}{'='*60}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{text:^60}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{'='*60}{Colors.ENDC}\n")


def print_success(text: str):
    print(f"{Colors.OKGREEN}✓ {text}{Colors.ENDC}")


def print_error(text: str):
    print(f"{Colors.FAIL}✗ {text}{Colors.ENDC}")


def print_info(text: str):
    print(f"{Colors.OKCYAN}→ {text}{Colors.ENDC}")


def print_warning(text: str):
    print(f"{Colors.WARNING}⚠ {text}{Colors.ENDC}")


def test_sql_injection():
    """SQL Injection 공격 테스트"""
    print_header("SQL Injection 공격 테스트")

    sql_payloads = [
        "1' OR '1'='1",
        "1' UNION SELECT NULL--",
        "1'; DROP TABLE users--",
        "1' AND 1=1--",
        "admin'--"
    ]

    for i, payload in enumerate(sql_payloads, 1):
        print_info(f"시도 {i}/5: {payload}")
        try:
            response = requests.get(
                f"{BASE_URL}/api/challenges/1",
                params={"search": payload},
                timeout=5
            )
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code})")
                break
            else:
                print_success(f"응답: {response.status_code}")
        except Exception as e:
            print_error(f"에러: {str(e)}")

        time.sleep(0.5)

    print_info("SQL Injection 테스트 완료")


def test_xss_attack():
    """XSS 공격 테스트"""
    print_header("XSS 공격 테스트")

    xss_payloads = [
        "<script>alert('XSS')</script>",
        "<img src=x onerror=alert(1)>",
        "javascript:alert(1)",
        "<iframe src='javascript:alert(1)'>",
        "<svg/onload=alert(1)>"
    ]

    for i, payload in enumerate(xss_payloads, 1):
        print_info(f"시도 {i}/5: {payload}")
        try:
            response = requests.get(
                f"{BASE_URL}/api/challenges/1",
                params={"name": payload},
                timeout=5
            )
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code})")
                break
            else:
                print_success(f"응답: {response.status_code}")
        except Exception as e:
            print_error(f"에러: {str(e)}")

        time.sleep(0.5)

    print_info("XSS 공격 테스트 완료")


def test_path_traversal():
    """Path Traversal 공격 테스트"""
    print_header("Path Traversal 공격 테스트")

    path_payloads = [
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "....//....//....//etc/passwd",
        "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
        "../../../../../../etc/shadow"
    ]

    for i, payload in enumerate(path_payloads, 1):
        print_info(f"시도 {i}/5: {payload}")
        try:
            response = requests.get(
                f"{BASE_URL}/api/files/{payload}",
                timeout=5
            )
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code})")
                break
            else:
                print_success(f"응답: {response.status_code}")
        except Exception as e:
            print_error(f"에러: {str(e)}")

        time.sleep(0.5)

    print_info("Path Traversal 테스트 완료")


def test_brute_force_flag():
    """플래그 브루트포스 공격 테스트"""
    print_header("플래그 브루트포스 공격 테스트")

    print_info("JWT 토큰 발급 중...")
    token = get_jwt_token()
    if not token:
        print_error("JWT 토큰 발급 실패")
        return

    print_success("JWT 토큰 발급 성공")

    headers = {"Authorization": f"Bearer {token}"}

    for i in range(1, 55):
        fake_flag = f"FLAG{{fake_flag_{i:03d}}}"
        print_info(f"시도 {i}/54: {fake_flag}")

        try:
            response = requests.post(
                f"{BASE_URL}/api/challenges/1/submit",
                json={"flag": fake_flag},
                headers=headers,
                timeout=5
            )
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code}) - {i}회 시도 후")
                break
            else:
                print_success(f"응답: {response.status_code}")
        except Exception as e:
            print_error(f"에러: {str(e)}")
            break

        time.sleep(0.1)

    print_info("플래그 브루트포스 테스트 완료")


def test_brute_force_login():
    """로그인 브루트포스 공격 테스트"""
    print_header("로그인 브루트포스 공격 테스트")

    for i in range(1, 55):
        fake_password = f"wrong_password_{i:03d}"
        print_info(f"시도 {i}/54: {fake_password}")

        try:
            response = requests.post(
                f"{BASE_URL}/api/auth/login",
                json={
                    "loginId": "test_user",
                    "password": fake_password
                },
                timeout=5
            )
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code}) - {i}회 시도 후")
                break
            else:
                print_success(f"응답: {response.status_code}")
        except Exception as e:
            print_error(f"에러: {str(e)}")
            break

        time.sleep(0.1)

    print_info("로그인 브루트포스 테스트 완료")


def test_rate_limiting():
    """Rate Limiting 테스트"""
    print_header("Rate Limiting 테스트")

    print_info("10초 동안 초당 30회씩 요청 (총 300회)")

    for i in range(1, 301):
        try:
            response = requests.get(f"{BASE_URL}/api/challenges", timeout=5)
            if response.status_code == 403:
                print_warning(f"차단됨! (Status: {response.status_code}) - {i}회 요청 후")
                break
            elif i % 50 == 0:
                print_success(f"{i}회 요청 완료")
        except Exception as e:
            print_error(f"에러: {str(e)}")
            break

        time.sleep(0.033)  # 초당 약 30회

    print_info("Rate Limiting 테스트 완료")


def get_jwt_token() -> Optional[str]:
    """테스트용 JWT 토큰 발급 (ADMIN 계정 사용)"""
    try:
        # ADMIN 계정으로 로그인 (회원가입 불필요)
        response = requests.post(
            f"{BASE_URL}/api/auth/login",
            json={
                "loginId": ADMIN_USER["loginId"],
                "password": ADMIN_USER["password"]
            },
            timeout=5
        )

        if response.status_code == 200:
            data = response.json()
            return data.get("data", {}).get("accessToken")

    except Exception as e:
        print_error(f"토큰 발급 실패: {str(e)}")

    return None


def main():
    print_header("CTF IP 차단 시스템 공격 테스트")

    print("테스트할 공격 유형을 선택하세요:")
    print("1. SQL Injection")
    print("2. XSS Attack")
    print("3. Path Traversal")
    print("4. Brute Force - Flag")
    print("5. Brute Force - Login")
    print("6. Rate Limiting")
    print("7. 모든 테스트 실행")
    print("0. 종료")

    choice = input("\n선택: ").strip()

    test_functions = {
        "1": test_sql_injection,
        "2": test_xss_attack,
        "3": test_path_traversal,
        "4": test_brute_force_flag,
        "5": test_brute_force_login,
        "6": test_rate_limiting,
    }

    if choice == "7":
        for func in test_functions.values():
            func()
            time.sleep(2)
    elif choice in test_functions:
        test_functions[choice]()
    elif choice == "0":
        print_info("종료합니다.")
        return
    else:
        print_error("잘못된 선택입니다.")

    print_header("테스트 완료")


if __name__ == "__main__":
    main()
