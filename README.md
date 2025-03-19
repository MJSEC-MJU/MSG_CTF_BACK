# MJSEC_CTF 대회 사이트 프로젝트

이 프로젝트는 MJSEC_CTF(Capture The Flag) 대회를 위한 웹 사이트로, CTFd를 사용하지 않고 직접 개발되었습니다. 이 문서는 프로젝트의 설치 방법, 기여자 정보, 시스템 아키텍처, 기술 스택, 협업 방식, 개발 기간, ERD, 그리고 구현된 기능을 설명합니다.

## 목차
1. [서버 설치 방법](#서버-설치-방법)
2. [기여자 표](#기여자-표)
3. [시스템 아키텍처](#시스템-아키텍처)
4. [기술 스택](#기술-스택)
5. [협업 방식](#협업-방식)
6. [개발 기간](#개발-기간)
7. [ERD](#erd)
8. [구현된 기능](#구현된-기능)

---

## 서버 설치 방법

아래 단계를 따라 서버를 설치하고 실행할 수 있습니다.

### 1. 저장소 복제
프로젝트는 백엔드, 프론트엔드, 디스코드 봇으로 나누어져 있습니다. 각 저장소를 복제합니다.

```bash
# 백엔드 저장소 복제
git clone https://github.com/MJSEC-CTF/backend.git
cd backend

# 프론트엔드 저장소 복제
git clone https://github.com/MJSEC-CTF/frontend.git
cd frontend

# 디스코드 봇 저장소 복제
git clone https://github.com/MJSEC-CTF/discord-bot.git
cd discord-bot
```
---
## 기여자 표
## Backend Team

| Profile                                                                 | Name         | Role                         | Area of Expertise             | GitHub Profile                                      |
|-------------------------------------------------------------------------|--------------|------------------------------|-------------------------------|-----------------------------------------------------|
| <img src="https://github.com/jongcoding.png" width="50" height="50" alt="jongcoding"> | Jongyun Lee  | Project Manager & DevOps     | Project Management, DevOps    | [jongcoding](https://github.com/jongcoding)         |
| <img src="https://github.com/minsoo0506.png" width="50" height="50" alt="minsoo0506">   | Minsoo Park  | Backend Developer            | Backend, Database             | [minsoo0506](https://github.com/minsoo0506)          |
| <img src="https://github.com/ORI-MORI.png" width="50" height="50" alt="ORI-MORI">       | Joo Oh Lee   | Backend Developer            | Backend, Ranking System       | [ORI-MORI](https://github.com/ORI-MORI)             |
| <img src="https://github.com/tember8003.png" width="50" height="50" alt="tember8003">   | Yuchan Jung  | Backend Developer            | Backend, User Logic           | [tember8003](https://github.com/tember8003)         |

## Frontend Team

| Profile                                                                 | Name   | Role                | Area of Expertise     | GitHub Profile                                          |
|-------------------------------------------------------------------------|--------|---------------------|-----------------------|---------------------------------------------------------|
| <img src="https://github.com/MEspeaker.png" width="50" height="50" alt="MEspeaker"> | 함선호 | Frontend Developer  | Frontend Development  | [MEspeaker](https://github.com/MEspeaker)               |
| <img src="https://github.com/jenn2i.png" width="50" height="50" alt="jenn2i">         | 박정은 | Frontend Developer  | Frontend Development  | [jenn2i](https://github.com/jenn2i)                     |
| <img src="https://github.com/youminki.png" width="50" height="50" alt="youminki">       | 유민기 | Frontend Developer  | Frontend Development  | [youminki](https://github.com/youminki)                 |

## Discord Bot Team

| Profile                                                                 | Bot Name         | Developer | GitHub Profile                                     |
|-------------------------------------------------------------------------|------------------|-----------|----------------------------------------------------|
| <img src="https://github.com/jongcoding.png" width="50" height="50" alt="jongcoding">   | FIRST_blood_bot  | 이종윤    | [jongcoding](https://github.com/jongcoding)        |
| <img src="https://github.com/jiyoon77.png" width="50" height="50" alt="jiyoon77">       | DJ_BOT           | 정지윤    | [jiyoon77](https://github.com/jiyoon77)            |
| <img src="https://github.com/tember8003.png" width="50" height="50" alt="tember8003">   | TICKET_BOT       | 정유찬    | [tember8003](https://github.com/tember8003)        |
| <img src="https://github.com/walnutpy.png" width="50" height="50" alt="walnutpy">       | ROLE_BOT         | 이주원    | [walnutpy](https://github.com/walnutpy)            |


---

## 시스템-아키텍처
![MJSECCTF drawio](https://github.com/user-attachments/assets/1257fdac-4325-4c3a-a94f-27f323842ab4)
