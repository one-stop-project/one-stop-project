# one-stop-project
내일배움캠프 팀 프로젝트


## 코드 리뷰 봇 설정
이 저장소는 PR 생성/업데이트 시 GitHub Actions 기반 AI 코드 리뷰 봇이 자동 실행됩니다.

### 동작 방식
- 워크플로우: `.github/workflows/code-review-bot.yml`
- 트리거: Pull Request 열림/업데이트/재오픈/Ready for review
- 봇 코멘트 언어: 한국어

### 필수 GitHub Secret
Repository Settings → Secrets and variables → Actions 에서 아래 값을 등록하세요.
- `OPENAI_API_KEY`: OpenAI API 키

> `GITHUB_TOKEN`은 GitHub Actions에서 자동 제공되므로 별도 생성이 필요 없습니다.
