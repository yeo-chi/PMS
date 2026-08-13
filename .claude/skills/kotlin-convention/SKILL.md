---
name: kotlin-convention
description: Kotlin/Spring Boot 코드를 작성, 수정, 리뷰할 때 반드시 참고해야 하는 팀 코드 컨벤션. 축약어 금지 네이밍, 메서드 체이닝 스타일, null 안전성 처리(!! 금지, requireNotNull/checkNotNull 사용) 등 이 프로젝트에서 통용되는 규칙을 담고 있다. Kotlin 파일(.kt)을 새로 작성하거나, 기존 .kt 코드를 수정하거나, PR/코드 리뷰를 할 때 이 스킬을 확인해서 규칙을 적용해야 한다.
---

# Kotlin 코드 컨벤션

이 프로젝트(예약 서버, 운영 서버 등 Kotlin + Spring Boot 3 + Java 21 기반 서브모듈)에서 Kotlin 코드를 작성할 때 따라야 하는 컨벤션이다. 공식 Kotlin 코딩 컨벤션과 업계에서 널리 쓰이는 스타일 가이드(JuulLabs, Unity, diktat)를 기반으로 정리했다.

새 코드를 작성하거나 기존 코드를 수정할 때, 아래 규칙과 충돌하는 부분이 있으면 이 문서의 규칙을 우선 적용한다.

## 1. 네이밍 — 축약어 금지

변수/함수/클래스/파라미터명에 축약어(abbreviation)를 사용하지 않는다. 전체 단어를 사용해 의도를 명확히 드러낸다.

```kotlin
// ❌ 금지
val usrAge: Int = 25
val addr: String = "123 Main St"
fun calc(p: Double, r: Double, m: Int): Double
class UAS

// ✅ 권장
val userAge: Int = 25
val address: String = "123 Main St"
fun calculateMonthlyPayment(principal: Double, rate: Double, months: Int): Double
class UserAuthenticationService
```

- 표준적으로 통용되는 약어(`URL`, `ID`, `HTML`, `API` 등)는 예외적으로 허용한다.
- 임의로 줄인 표현(`Ttl`, `Mgr`, `Req`, `Rsp` 등)은 금지한다.
- 단, `id`처럼 이미 도메인 필드명으로 굳어진 짧은 이름은 허용한다. 판단 기준은 "이 이름만 보고 팀원 전체가 즉시 뜻을 알 수 있는가"이다.

## 2. 메서드 체이닝(Chaining) 활용

컬렉션 연산, 빌더 패턴 등에서는 명령형 반복문(`for`, 중첩 `if`)보다 표준 라이브러리 체이닝을 우선 사용한다.

```kotlin
// ❌ 지양
val activeUserNames = mutableListOf<String>()
for (user in users) {
    if (user.isActive) {
        activeUserNames.add(user.name)
    }
}

// ✅ 권장
val activeUserNames = users
    .filter { it.isActive }
    .map { it.name }
    .sorted()
```

- 체이닝이 2개 이상 이어지면 한 줄에 몰아쓰지 않고, `.` 앞에서 줄바꿈하여 각 단계를 한눈에 볼 수 있게 한다.
- 체이닝 안의 람다에서 `it`이 명확하지 않은 경우(중첩 람다, 의미가 헷갈리는 경우)에는 명시적 파라미터명을 사용한다.

```kotlin
// 중첩 람다처럼 it이 모호해질 수 있는 경우
reservations
    .filter { reservation -> reservation.status == ReservationStatus.CONFIRMED }
    .map { reservation -> reservation.reservationNo }
```

- 다만 체이닝이 과도하게 길어져 오히려 읽기 어려워지면, 중간 변수로 쪼개거나 private 함수로 추출해 가독성을 우선한다.

## 3. Null 안전성 — `!!` 대신 `requireNotNull` / `checkNotNull` 사용

`!!`(non-null assertion) 연산자는 원칙적으로 사용하지 않는다. 컴파일 타임에 보장되던 null 안전성을 런타임 예외(NPE)로 되돌리는 안티패턴이기 때문이다.

상황에 맞는 명시적 함수를 사용한다.

| 상황 | 사용 함수 | 예시 |
|---|---|---|
| 함수 파라미터/사전조건 검증 | `requireNotNull` | `requireNotNull(request.roomCode) { "roomCode must not be null" }` |
| 이미 초기화되어 있어야 하는 상태값 검증 | `checkNotNull` | `checkNotNull(cachedReservation) { "reservation should already be loaded" }` |
| 대체 기본값이 있는 경우 | 안전 호출(`?.`) + 엘비스 연산자(`?:`) | `val name = user?.name ?: "UNKNOWN"` |
| null이면 그냥 넘어가도 되는 경우 | `?.let { }` | `reservation?.let { notify(it) }` |

```kotlin
// ❌ 금지
val roomCode = request.roomCode!!

// ✅ 권장
val roomCode = requireNotNull(request.roomCode) { "roomCode must not be null" }
```

`!!`를 정말 피할 수 없는 경우(예: nullability 마커가 없는 Java 라이브러리 상호운용, 컴파일러가 스마트 캐스트를 못 하는 상황)에만 예외적으로 허용하되, **왜 안전한지 사유를 주석으로 반드시 남긴다.**

```kotlin
// Java 레거시 라이브러리가 nullability 마커를 제공하지 않지만,
// 이 시점에는 SDK 내부적으로 항상 초기화가 보장됨이 문서화되어 있음
val legacyValue = legacyJavaClient.getValue()!! // TODO: 레거시 SDK 교체 시 제거
```

### 3.1 Optional 타입 금지

Java의 `Optional<T>`은 Kotlin에서 사용하지 않는다. Kotlin의 nullable 타입(`T?`)이 이미 같은 역할을 하며 컴파일러가 강제해주기 때문이다. Java API가 `Optional<T>`을 반환하면 경계(boundary)에서 바로 변환한다.

```kotlin
val value: String? = javaApi.getOptionalValue().getOrNull()
```

## 4. 그 외 기본 원칙 (Kotlin 공식 컨벤션 기반)

- 패키지명은 모두 소문자, 밑줄(`_`) 사용하지 않음.
- 클래스/객체명은 UpperCamelCase, 함수/변수명은 lowerCamelCase.
- 상수(`const val`)는 대문자 + 밑줄 구분 (`MAX_RETRY_COUNT`).
- 함수가 단일 표현식으로 작성 가능하면 표현식 본문(expression body)을 사용한다.

```kotlin
// ✅ 권장
fun isConfirmed(reservation: Reservation): Boolean = reservation.status == ReservationStatus.CONFIRMED
```

- data class를 적극 활용하고, 불필요한 가변 상태(`var`)보다 불변(`val`)을 기본으로 한다.
- 확장 함수(extension function)는 이미 존재하는 표준 라이브러리 기능을 재정의하지 않도록 주의한다.

## 5. 정적 분석 도구

실제 적용 시 ktlint 또는 detekt로 위 규칙(특히 네이밍, `!!` 사용 금지)을 CI에서 강제하는 것을 권장한다.

## 참고 자료

- [Kotlin 공식 코딩 컨벤션 (kotlinlang.org)](https://kotlinlang.org/docs/coding-conventions.html)
- [JuulLabs Kotlin Style Guide](https://github.com/JuulLabs/kotlin-guides)
- [Unity Kotlin Style Guide](https://unity-technologies.github.io/kotlin-guide/)
- [diktat Kotlin Coding Convention](https://github.com/saveourtool/diktat/blob/master/info/guide/diktat-coding-convention.md)
