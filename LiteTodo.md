# QuantMA Lite — Остаток задач

> Мастер-версия: `D:\LocalLLm\CodeAgent` (не трогать)
> Lite-репо: `D:\LocalLLm\QuantMA-Lite` / https://github.com/3domstranger-code/-QuantMA-Lite
> APK собран: `app/build/outputs/apk/debug/app-debug.apk` (79 MB, Mar 23 2026)

---

## 1. Установка и ручное тестирование

- [ ] Передать APK на устройство (ADB или Telegram)
- [ ] Установить и запустить
- [ ] Проверить: Chat Mode работает (загрузка модели, стриминг)
- [ ] Проверить: `read_file` — читает файл, возвращает ToolCallCard
- [ ] Проверить: `list_files` — листинг директории
- [ ] Проверить: `search_files` — поиск по regex
- [ ] Проверить: `git_status` — статус репо
- [ ] Проверить: `write_file` → показывает "⚡ Available in QuantMA Ultra"
- [ ] Проверить: имя приложения "Квант" / "QuantMA Lite" (не "CodeAgent")
- [ ] Проверить: DB не крашит (Room version=1, quantma.db)

---

## 2. Скриншоты (DEMO_TESTS.md)

Порядок съёмки: 10 → 8 → 7 → 1 → 2 → 9 → 3 → 4 → 5 → 6

- [ ] Тест 10: Настройки Simple Mode + Advanced Mode
- [ ] Тест 8: Редактор кода (Sora Editor, тема Dracula)
- [ ] Тест 7: Файловый менеджер, контекстное меню
- [ ] Тест 1: Каталог моделей, карточка модели
- [ ] Тест 2: Чат — объяснение кода (streaming + финальный ответ)
- [ ] Тест 9: Производительность — tok/s во время генерации
- [ ] Тест 3: Агент — `read_file` + ToolCallCard
- [ ] Тест 4: Агент — write_file → Ultra upsell диалог (вместо подтверждения)
- [ ] Тест 5: Агент — git_status + цепочка карточек
- [ ] Тест 6: Агент — search_files по regex

---

## 3. Хабр-анонс

- [ ] Добавить скриншоты в `HABR_ANNOUNCEMENT.md`
- [ ] Финализировать текст (проверить актуальность: 5 инструментов в Lite, не 7)
- [ ] Обновить таблицу Lite vs Ultra в соответствии с реальным состоянием
- [ ] Опубликовать на Хабре

---

## 4. GitHub Release

- [ ] Собрать release APK (R8 включён, без debug symbols)
  ```
  gradlew.bat assembleRelease
  ```
  > Нужна подпись или zipalign + apksigner вручную; либо оставить debug для демо
- [ ] Создать тег `v1.0.0-lite`
  ```
  git tag v1.0.0-lite && git push origin v1.0.0-lite
  ```
- [ ] GitHub Release с APK как asset и кратким changelog

---

## 5. Иконка и брендинг (перед релизом)

- [ ] Разработать финальную иконку (сейчас заглушка от CodeAgent)
- [ ] Обновить `ic_launcher` / `ic_launcher_round` в mipmap
- [ ] Опционально: splash screen с "Квант" / "QuantMA"

---

## 6. Юридика и атрибуция

- [ ] Экран "О приложении" / лицензионная атрибуция (llama.cpp MIT, JGit EDL, Sora LGPL-2.1)
- [ ] Privacy Policy доступна в приложении (уже есть `PRIVACY_POLICY.md` в репо — нужен in-app экран)

---

## 7. Будущее (после анонса)

- [ ] O-MVLL для нативного кода (`llama-jni.cpp`) — доп. защита
- [ ] `git_branch` и `git_stash_list` — проверить что работают в Lite (они read-only, оставлены)
- [ ] Unit-тесты: починить сломанные после удаления TaskPlanner/RAG (если нужны)
- [ ] QuantMA Ultra roadmap — write tools, TaskPlanner, RAG, ModelRouter, Guided Mode

---

## Известные проблемы

| Проблема | Статус |
|----------|--------|
| Мультимодальность — UI для передачи изображений не сделан | В мастере тоже не готово |
| Hexagon HTP — не проверен на реальном железе | Отключён в Lite (hexagonEnabled=false) |
| `fallbackToDestructiveMigration()` deprecated warning | Не критично, работает |
