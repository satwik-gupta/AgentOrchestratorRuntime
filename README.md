# Agent Orchestrator Runtime

Lightweight Java runtime for orchestrating modular agents, tools, vector stores, and workflows. Built for local development and demos that integrate LLM backends (Ollama/OpenAI).

---

## Architecture & System Design (The "What")

- **Language & Runtime:** Java (compiled for Java 21 / OpenJDK 21 as declared in the parent POM).
- **Build Tool:** Apache Maven (3.8+ recommended).
- **Key Libraries:** Jackson (JSON serialization/deserialization). The project uses plain Java SE APIs (for HTTP, concurrency, etc.) — no heavyweight frameworks like Spring Boot are included.
- **Testing:** No testing framework is declared in the module POMs; add JUnit 5 (and Maven Surefire) if you add unit tests.

Directory layout (top-level modules):

```
agent-orchestrator (root POM)
├─ agent-core        # core models, agent state machine, messages
├─ agent-workflow    # workflow graph, task execution, retry policies
├─ agent-tools       # tool interfaces, dispatcher, sample tool plugins
├─ agent-vectorstore # simple in-memory vector store + embeddings
├─ agent-network     # event broker, network/server/client primitives
└─ agent-bootstrap   # sample runners, demos, LLM client adapters
```

Quick system flow (high-level): agent-bootstrap wires together `agent-core`, `agent-tools`, `agent-vectorstore`, `agent-network`, and `agent-workflow`. The demo shows a researcher agent executing search tasks against the vector store and a writer agent streaming LLM output.

---

## Prerequisites & Environment Setup

### Prerequisites
- JDK 21 (OpenJDK 21) — matches `<maven.compiler.release>` in the parent POM.
- Apache Maven 3.8+.
- An IDE (VS Code with the Java Extension Pack, or IntelliJ IDEA).

### Environment variables
The demo in `agent-bootstrap` uses the following environment variables (optional):

- `LLM_BASE_URL` — base URL for your LLM provider (defaults to `https://api.openai.com` when unset in the demo).
- `OPENAI_API_KEY` — your OpenAI API key (if present the demo will run in OpenAI mode; if omitted the client can be used against local Ollama endpoints).

Example `.env` (Linux/macOS):

```
LLM_BASE_URL=https://api.openai.com
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Windows PowerShell example:

```powershell
# $Env:LLM_BASE_URL = "https://api.openai.com"
# $Env:OPENAI_API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

The demo code will fall back to `https://api.openai.com` if `LLM_BASE_URL` is not provided. `OPENAI_API_KEY` is optional (required only if you want to call OpenAI directly).

---

## Getting Started & Build Instructions

Follow these steps from the repository root.

1) Verify Java and Maven are available:

```bash
java -version
mvn -version
```

2) (Optional) configure environment variables. Example for PowerShell (Windows):

```powershell
$Env:LLM_BASE_URL = "https://api.openai.com"
$Env:OPENAI_API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

3) Build the multi-module project (fast iteration without tests):

```bash
mvn -B -DskipTests clean package
```

4) Run the included demo (`ProductionOrchestrationDemo`) which wires the engine, vector store and a simple LLM client.

Windows / cross-platform (Maven exec):

```bash
mvn -pl agent-bootstrap -am org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.agentorchestrator.bootstrap.ProductionOrchestrationDemo
```

Alternatively, run the `com.agentorchestrator.bootstrap.ProductionOrchestrationDemo` main class directly from your IDE after building.

Notes:
- `-pl agent-bootstrap -am` builds `agent-bootstrap` and all modules it depends on.
- If you prefer a single-file run with classpath, assemble the modules and run `java -cp` including the module `target/classes` directories (the Maven exec approach above is simpler for development).

---

## Running Tests

Run the test suite (if/when tests are added) with Maven:

```bash
mvn test
```

If you want a quicker build during development (skip tests):

```bash
mvn -DskipTests clean package
```

---

## Notes & Next Steps

- The project is intentionally framework-light: it favors simple Java APIs and small modules so you can iterate quickly.
- To add tests: add JUnit 5 (Jupiter) as a test dependency in the modules and configure the Maven Surefire plugin if needed.
- To integrate a hosted LLM or a different client, implement an adapter that conforms to the `OllamaOrOpenAiClient`-style usage from `agent-bootstrap` and wire it into `AgentEngineBootstrap`.

