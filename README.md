# Memoria 안전 (Android)

동의 기반 개인 안전 앱입니다. 사용자가 직접 발동한 SOS와, 사용자가 켠 패턴 감지 시
사용자가 지정한 보호자에게 **위치·알림·증거**를 전송합니다. 응급기관에 자동으로
전화하지 않으며, 응급 서비스를 대체하지 않습니다.

> 이 저장소는 **투명 버전**입니다. 은밀 녹음·위장 종료 화면·앱 숨김 기능은 포함하지
> 않습니다. 감지·수집이 실행될 때는 Android 포그라운드 알림과 시스템 표시가 유지되며,
> 앱이 이를 끄거나 숨기지 않습니다.

## 기능

- SOS 버튼(3회 탭 / 3초 길게) → 보호자에게 알림
- 톡톡/흔들기 패턴 감지(사용자가 켠 경우에만)
- 위치 전송: 좌표 + 지도 링크 + 최근 경로(KML)
- 증거 수집: 감지 시점 전후 음성(영상 없음), 사용자가 허용한 경우에 한해 화면 캡처
- Telegram 봇으로 지정 수신자에게 전송 (이메일은 선택 기능이며 현재 보류)
- 오프라인 시 로컬 보관 후 연결 복구 시 재전송

## 동의와 통제

- 첫 실행 시 동의 화면을 거쳐야 사용 가능
- 감지·수집·위치 공유가 실행 중이면 포그라운드 알림으로 표시
- 설정에서 언제든 감지·공유를 끌 수 있음

## 사용 안내 / Documentation

- 설치: [한국어](docs/INSTALL.ko.md) · [English](docs/INSTALL.en.md)
- 사용자 안내서: [한국어](docs/USER_GUIDE.ko.md) · [English](docs/USER_GUIDE.en.md)

## 빌드

요구 환경: Android SDK 36, JDK 17 이상, Gradle 8.13(포함된 launcher 사용)

```bash
cd android
./gradlew test
./gradlew assembleDebug
./gradlew bundleRelease   # Play 제출용 AAB (로컬 서명 필요)
```

서명 키는 저장소에 포함하지 않습니다. `android/keystore.properties`를 로컬에서 만들어
사용하고, 키스토어와 암호는 절대 커밋하지 마세요.

## 개인정보

- 개인정보처리방침(호스팅): https://jicine1360-prog.github.io/memoria-safe/
- 문서: [개인정보처리방침](PRIVACY_DATA_SAFETY.md) · [Data Safety 초안](DATA_SAFETY.md)

## 라이선스

MIT — [LICENSE](LICENSE)

## 상태

개발/파일럿 단계입니다. 스토어 제출 전 다음이 필요합니다.

- [x] 로컬 증거 자동 삭제 구현(보존 7일) 및 개인정보 문서에 고지
- [x] 이메일 전송 보류(Telegram 중심) — 설정 UI 숨김, 비상 흐름에서 호출 제거
- [x] 서버 미사용(Telegram 직접 전송) — 분실 모드도 Telegram 명령(/lost, /stop) 방식
- [x] 개인정보처리방침 URL 호스팅, 연락처(cloudseha@gmail.com / @Memoriasafe_bot)
- [x] MIT 라이선스
- [ ] Play Console Data Safety 양식 입력
- [ ] 콘텐츠 등급(IARC) 설문, 스크린샷/스토어 자산 등록
- [ ] 백그라운드 위치·마이크·화면 캡처 권한 사유 및 데모 영상
- [ ] release 서명 키스토어(로컬) 준비 및 AAB 서명
