# 📘 project\_plan.md

## 🧩 프로젝트 개요

* 이 프로젝트는 Cursor 에디터에서 Git MCP를 활용하여 코드를 자동 저장하고 변경 내역을 관리하는 데 목적이 있어요.
* MCP를 통해 Claude, ChatGPT, Gemini 등 AI가 Git 작업을 도와줄 수 있어요.

## 🧱 개발 환경

* 사용 클라이언트: **Cursor**
* 프로젝트 루트 경로: `C:\xampp\htdocs`
* 사용 MCP 목록:

  * Git MCP

## ⚙️ Cursor 설정

* 지침 입력 위치: `Settings > Rules`
* 텍스트 편집기 MCP / 파일 시스템 MCP: 생략 (Cursor 자체 제공)
* 트리거 키워드 설정: 생략 가능

## 📦 Git MCP 설정 방법

1. Git 설치되어 있어야 해요 (이미 설치됨).
2. Git MCP 설치:

   ```bash
   pip install mcp-server-git
   ```
3. Git 저장소 초기화:

   ```bash
   cd C:\xampp\htdocs
   git init
   ```
4. Cursor 설정에 다음 추가:

   ```json
   {
     "command": "mcp-server-git",
     "args": [
       "--repository",
       "C:\\xampp\\htdocs"
     ]
   }
   ```

## 🔧 Git MCP 사용 예시 (AI가 사용할 JSON)

### ✅ Git 초기화 & 첫 커밋

```json
{
  "tool": "git",
  "parameters": {
    "subtool": "RunCommand",
    "path": "C:/xampp/htdocs",
    "command": "cmd",
    "args": [
      "/c",
      "git init && git add . && git commit -m \"chore: initial commit\""
    ]
  }
}
```

### ✅ 특정 파일 커밋

```json
{
  "tool": "git",
  "parameters": {
    "subtool": "RunCommand",
    "path": "C:/xampp/htdocs",
    "command": "cmd",
    "args": [
      "/c",
      "git add index.php && git commit -m \"feat: update index page\""
    ]
  }
}
```

### ✅ 테스트 후 자동 커밋

```json
{
  "tool": "git",
  "parameters": {
    "subtool": "RunCommand",
    "path": "C:/xampp/htdocs",
    "command": "cmd",
    "args": [
      "/c",
      "npm test && git add . && git commit -m \"test: auto commit\""
    ]
  }
}
```

## 📌 참고 사항

* `project_plan.md`는 작업이 있을 때마다 AI가 이 문서를 계속 업데이트하도록 해야 해요.
* Git MCP는 **명령어를 기록하고 실행하는 역할**이기 때문에, AI가 뭔가 저장하거나 기록할 때 자동으로 사용하게 돼요.
* 잘 작동하는지 확인하려면 Cursor에서 AI에게 "git으로 변경사항 저장해줘"라고 말해보세요.

---

잘 안되면 언제든지 다시 물어보세요! 😊 