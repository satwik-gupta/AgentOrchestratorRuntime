# Contributing to Agent Orchestrator Runtime

Thanks for helping improve this project — this document explains local developer setup, workflow tips, and a lightweight pre-commit hook pattern you can enable locally.

## Quick Local Development Checklist

- Install JDK 21 (OpenJDK 21) and Apache Maven 3.8+.
- Clone the repository and open it in your IDE (VS Code or IntelliJ recommended).
- Build once to download dependencies and verify the project compiles:

```bash
git clone <repo-url>
cd "Agent Orchestrator Runtime - JAVA"
mvn -B -DskipTests clean package
```

### Run the demo

From the project root you can run the bundled demo:

```bash
mvn -pl agent-bootstrap -am org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.agentorchestrator.bootstrap.ProductionOrchestrationDemo
```

### Environment variables

The demo and the LLM client read the following environment variables:

- `LLM_BASE_URL` — base URL for your LLM provider (defaults to `https://api.openai.com`).
- `OPENAI_API_KEY` — (optional) OpenAI API key; when present the demo will run in OpenAI mode.

Example (PowerShell):

```powershell
$Env:LLM_BASE_URL = "https://api.openai.com"
$Env:OPENAI_API_KEY = "sk-xxxx..."
```

## IDE & tooling recommendations

- VS Code: install the **Java Extension Pack** (vscjava.vscode-java-pack), **GitLens** and **EditorConfig**.
- IntelliJ IDEA: open the root `pom.xml` and let the IDE import the Maven project.
- Consider adding a code formatter (Spotless / google-java-format) if you want strictly enforced formatting.

## Branches, PRs, and commits

- Branch naming: `feature/`, `fix/`, `chore/` (e.g., `feature/add-tool-registry`).
- Keep PRs small and focused. Include a short description, link to any related issue, and mention how to test locally.
- Use Conventional Commits for clarity (optional but recommended): `feat:`, `fix:`, `chore:`, etc.

## Tests & CI

- Run unit tests with:

```bash
mvn test
```

- For local verification before opening a PR, run a quick compile for changed modules or a full build with:

```bash
mvn -B -DskipTests clean package
```

CI (GitHub Actions) should run a full `mvn -B -DskipTests=false clean verify` on pushes and PRs; consider adding a lightweight workflow to run fast checks and a separate workflow for long-running integration tests.

## Pre-commit hooks (recommended)

This repo recommends a small, safe pre-commit hook that compiles any modules affected by the staged changes. We use a local `.githooks` directory so hooks can be versioned in the repo.

1. Create a `.githooks` directory at the repo root.
2. Add the hook files shown below to `.githooks`.
3. Enable hooks (one-time per clone):

```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
```

POSIX shell sample (`.githooks/pre-commit`):

```sh
#!/bin/sh
set -e
STAGED=$(git diff --cached --name-only --diff-filter=ACM)
if [ -z "$STAGED" ]; then exit 0; fi
MODULES=""
for f in $STAGED; do
  case "$f" in
    agent-core/*) MODULES="$MODULES,agent-core";;
    agent-workflow/*) MODULES="$MODULES,agent-workflow";;
    agent-tools/*) MODULES="$MODULES,agent-tools";;
    agent-vectorstore/*) MODULES="$MODULES,agent-vectorstore";;
    agent-network/*) MODULES="$MODULES,agent-network";;
    agent-bootstrap/*) MODULES="$MODULES,agent-bootstrap";;
  esac
done

# normalize and dedupe
MODULES=$(echo "$MODULES" | sed 's/^,//' | tr ',' '\n' | awk '!seen[$0]++' | tr '\n' ',' | sed 's/,$//')
if [ -z "$MODULES" ]; then exit 0; fi
echo "Compiling modules: $MODULES"
mvn -q -pl "$MODULES" -am -DskipTests compile

exit 0
```

PowerShell sample (`.githooks/pre-commit.ps1`):

```powershell
$staged = git diff --cached --name-only --diff-filter=ACM
if (-not $staged) { exit 0 }
$modules = @()
foreach ($f in $staged) {
  if ($f -like 'agent-core/*') { $modules += 'agent-core' }
  if ($f -like 'agent-workflow/*') { $modules += 'agent-workflow' }
  if ($f -like 'agent-tools/*') { $modules += 'agent-tools' }
  if ($f -like 'agent-vectorstore/*') { $modules += 'agent-vectorstore' }
  if ($f -like 'agent-network/*') { $modules += 'agent-network' }
  if ($f -like 'agent-bootstrap/*') { $modules += 'agent-bootstrap' }
}
$modules = $modules | Select-Object -Unique
if ($modules.Count -eq 0) { exit 0 }
$modulesArg = $modules -join ','
Write-Host "Compiling modules: $modulesArg"
mvn -q -pl $modulesArg -am -DskipTests compile
if ($LASTEXITCODE -ne 0) { Write-Error "maven compile failed"; exit 1 }
exit 0
```

Notes on the hook above:

- It compiles changed modules only (faster than a full build) and fails the commit if compilation fails.
- The scripts are intentionally minimal: they do not run tests or modify code. If you want formatting/linters (Spotless, Checkstyle), add those to the build and invoke their checks here.

### Manual run & troubleshooting

- Run the hook manually for testing:

```bash
./.githooks/pre-commit
```

- If the hook prevents your commit and you need to bypass it (only when necessary), use:

```bash
git commit --no-verify
```

## Adding tests

- Add JUnit 5 (Jupiter) to the module `pom.xml` where you add tests. Example dependency:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.0</version>
  <scope>test</scope>
</dependency>
```

## Need help?

- Open an issue for design or build problems.
- For small changes, open a PR and tag a reviewer.

Thank you for contributing — small, well-tested PRs are most welcome.
