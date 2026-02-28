# MCP Jira Cloud Tools

Spring Boot starter che fornisce tool MCP per Jira Cloud (issue, progetti, board, sprint, commenti, utenti) via REST API v3 e Agile API 1.0. Pubblicato su Maven Central come `io.github.massimilianopili:mcp-jira-tools`.

## Build

```bash
# Build
/opt/maven/bin/mvn clean compile

# Install locale (senza GPG)
/opt/maven/bin/mvn clean install -Dgpg.skip=true

# Deploy su Maven Central (richiede GPG + credenziali Central Portal in ~/.m2/settings.xml)
/opt/maven/bin/mvn clean deploy
```

Java 17+ richiesto. Maven: `/opt/maven/bin/mvn`.

## Struttura Progetto

```
src/main/java/io/github/massimilianopili/mcp/jira/
├── JiraProperties.java              # @ConfigurationProperties(prefix = "mcp.jira")
├── JiraConfig.java                  # WebClient bean (Basic Auth email:apiToken)
├── JiraToolsAutoConfiguration.java  # Spring Boot auto-config
├── JiraIssueTools.java              # @ReactiveTool: ricerca JQL, CRUD issue, transizioni
├── JiraProjectTools.java            # @ReactiveTool: progetti, tipi issue, priorita', stati
├── JiraBoardTools.java              # @ReactiveTool: board Scrum/Kanban, configurazione
├── JiraSprintTools.java             # @ReactiveTool: sprint, issue sprint, backlog
├── JiraCommentTools.java            # @ReactiveTool: commenti, changelog
└── JiraUserTools.java               # @ReactiveTool: utente corrente, ricerca utenti

src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Tool (24 totali)

### JiraIssueTools (7)
- `jira_search_issues` — Cerca issue con JQL (POST `/rest/api/3/search`)
- `jira_get_issue` — Dettaglio issue per chiave (GET `/rest/api/3/issue/{key}`)
- `jira_create_issue` — Crea issue: Story, Task, Bug, Epic (POST `/rest/api/3/issue`)
- `jira_update_issue` — Aggiorna campi issue (PUT `/rest/api/3/issue/{key}`)
- `jira_get_transitions` — Transizioni disponibili (GET `/rest/api/3/issue/{key}/transitions`)
- `jira_transition_issue` — Cambia stato issue (POST `/rest/api/3/issue/{key}/transitions`)
- `jira_delete_issue` — Elimina issue (DELETE `/rest/api/3/issue/{key}`)

### JiraProjectTools (5)
- `jira_list_projects` — Lista progetti (GET `/rest/api/3/project`)
- `jira_get_project` — Dettaglio progetto (GET `/rest/api/3/project/{key}`)
- `jira_list_issue_types` — Tipi issue disponibili (GET `/rest/api/3/issuetype`)
- `jira_list_priorities` — Priorita' disponibili (GET `/rest/api/3/priority`)
- `jira_list_statuses` — Stati disponibili (GET `/rest/api/3/status`)

### JiraBoardTools (3)
- `jira_list_boards` — Board Scrum/Kanban (GET `/rest/agile/1.0/board`)
- `jira_get_board` — Dettaglio board (GET `/rest/agile/1.0/board/{id}`)
- `jira_get_board_configuration` — Configurazione: colonne, filtro (GET `/rest/agile/1.0/board/{id}/configuration`)

### JiraSprintTools (4)
- `jira_list_sprints` — Sprint di una board (GET `/rest/agile/1.0/board/{boardId}/sprint`)
- `jira_get_sprint` — Dettaglio sprint (GET `/rest/agile/1.0/sprint/{sprintId}`)
- `jira_get_sprint_issues` — Issue nello sprint (GET `/rest/agile/1.0/sprint/{sprintId}/issue`)
- `jira_get_backlog_issues` — Issue nel backlog (GET `/rest/agile/1.0/board/{boardId}/backlog`)

### JiraCommentTools (3)
- `jira_list_comments` — Commenti di una issue (GET `/rest/api/3/issue/{key}/comment`)
- `jira_add_comment` — Aggiungi commento ADF (POST `/rest/api/3/issue/{key}/comment`)
- `jira_get_changelog` — Storico modifiche (GET `/rest/api/3/issue/{key}/changelog`)

### JiraUserTools (2)
- `jira_get_current_user` — Utente corrente (GET `/rest/api/3/myself`)
- `jira_search_users` — Cerca utenti (GET `/rest/api/3/user/search`)

## Pattern Chiave

- **@ReactiveTool** (spring-ai-reactive-tools): metodi asincroni che restituiscono `Mono<T>`. Auto-registrati da `ReactiveToolAutoConfiguration` — nessun `ToolCallbackProvider` necessario.
- **Auto-configuration**: `JiraToolsAutoConfiguration` si attiva con `@ConditionalOnProperty(name = "mcp.jira.api-token")`.
- **Dual API**: Platform API (`/rest/api/3/`) per issue, progetti, utenti + Agile API (`/rest/agile/1.0/`) per board e sprint.
- **ADF**: Jira API v3 usa Atlassian Document Format (JSON strutturato). I tool convertono testo semplice via `toAdf()` e estraggono testo da ADF via `extractTextFromAdf()`.
- **WebClient**: Basic Auth `email:apiToken` (base64). Buffer 5MB.

## Configurazione

```properties
# Obbligatorie — abilitano tutti i tool Jira
MCP_JIRA_BASE_URL=https://myorg.atlassian.net
MCP_JIRA_EMAIL=user@example.com
MCP_JIRA_API_TOKEN=your-api-token
```

API token: https://id.atlassian.com/manage-profile/security/api-tokens

## Dipendenze

- Spring Boot 3.4.1 (spring-boot-autoconfigure, spring-boot-starter-webflux)
- Spring AI 1.0.0 (spring-ai-model)
- spring-ai-reactive-tools 0.2.0

## Maven Central

- GroupId: `io.github.massimilianopili`
- Plugin: `central-publishing-maven-plugin` v0.7.0
- Credenziali: Central Portal token in `~/.m2/settings.xml` (server id: `central`)
