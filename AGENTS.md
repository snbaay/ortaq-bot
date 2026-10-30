# Ortaq Bot (Homebot)

Telegram-бот для учета совместных расходов соседей/сожителей.

## Стек технологий

- **Язык**: Java 21
- **Фреймворк**: Spring Boot 3.3.4 (Spring Web, Spring Data JPA)
- **Telegram API**: Telegram Bots Spring Boot Starter 7.11.0 (Long Polling)
- **Базы данных**:
  - H2 Database (профиль `dev`, файловая БД в `./data/homebot`, in-memory для тестов)
  - PostgreSQL (профиль `prod`)
- **Сборка**: Maven Wrapper (`mvnw` / `mvnw.cmd`)
- **Тестирование**: JUnit 5, Spring Boot Test, AssertJ

---

## Структура пакетов (`kz.homebot`)

- **`bot`**: `HomeTelegramBot` — обработка входящих команд и сообщений Telegram, взаимодействие с пользователями.
- **`config`**: `AppConfig`, `HealthController` (`GET /health`).
- **`domain`**: JPA-сущности — `Household`, `Member`, `Expense`, `ExpenseShare`.
- **`repository`**: Spring Data JPA репозитории для доступа к БД (`HouseholdRepository`, `MemberRepository`, `ExpenseRepository`, `ExpenseShareRepository`).
- **`service`**: Бизнес-логика приложения:
  - `ExpenseService` / `ExpenseSplitter` / `ExpenseParser` — учет, разбор и распределение расходов.
  - `BalanceService` / `DebtCalculator` — баланс и взаиморасчеты между участниками.
  - `MemberRegistrationService` — регистрация участников и домохозяйств.
  - `MessageService` / `MoneyFormatter` — локализация (RU / KK) и форматирование сообщений.
- **`src/main/resources`**: Конфигурация `application.yml`, файлы локализации `messages.properties` и `messages_kk.properties`.

---

## Запуск приложения

### 1. Переменные окружения

Перед запуском укажите токен и имя бота (см. `.env.example`):
```bash
BOT_TOKEN=<your_bot_token>
BOT_USERNAME=<your_bot_username>
```

### 2. Команды запуска

**Разработка (`dev` профиль по умолчанию, используется H2):**
- Windows:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- Linux / macOS:
  ```bash
  ./mvnw spring-boot:run
  ```

**Продакшн (`prod` профиль, PostgreSQL):**
- Дополнительно требуются: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Запуск с профилем:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
  ```

### 3. Служебные эндпоинты

- Healthcheck: `http://localhost:8080/health`
- H2 Console (только в `dev`): `http://localhost:8080/h2-console`

---

## Тестирование

### Запуск всех тестов

- Windows:
  ```powershell
  .\mvnw.cmd test
  ```
- Linux / macOS:
  ```bash
  ./mvnw test
  ```

### Запуск отдельного теста

```bash
./mvnw test -Dtest=ExpenseParserTest
```
