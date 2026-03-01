# MCP Jira Cloud Tools

Spring Boot starter providing 24 MCP tools for Jira Cloud. Covers issues (JQL search, CRUD, transitions), projects, Scrum/Kanban boards, sprints, comments, and users via the Jira REST API v3 and Agile API 1.0.

## Installation

```xml
<dependency>
    <groupId>io.github.massimilianopili</groupId>
    <artifactId>mcp-jira-tools</artifactId>
    <version>0.0.1</version>
</dependency>
```

Requires Java 17+, Spring AI 1.0.0+, and [spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools) 0.2.0+.

## Tools (24)

| Class | Count | Description |
|-------|-------|-------------|
| `JiraIssueTools` | 7 | JQL search, get/create/update/delete issue, transitions |
| `JiraProjectTools` | 5 | List projects, issue types, priorities, statuses |
| `JiraBoardTools` | 3 | List Scrum/Kanban boards, board configuration |
| `JiraSprintTools` | 4 | List sprints, sprint issues, backlog |
| `JiraCommentTools` | 3 | List/add comments (ADF format), changelog |
| `JiraUserTools` | 2 | Current user, search users |

## Configuration

```properties
# Required — enables all Jira tools
MCP_JIRA_BASE_URL=https://myorg.atlassian.net
MCP_JIRA_EMAIL=user@example.com
MCP_JIRA_API_TOKEN=your-api-token
```

Generate an API token at https://id.atlassian.com/manage-profile/security/api-tokens

## How It Works

- Uses `@ReactiveTool` ([spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools)) for async `Mono<T>` methods
- Auto-configured via `JiraToolsAutoConfiguration` with `@ConditionalOnProperty(name = "mcp.jira.api-token")`
- Dual API: Platform (`/rest/api/3/`) for issues and projects + Agile (`/rest/agile/1.0/`) for boards and sprints
- Atlassian Document Format (ADF) handling: plain text auto-converted to/from ADF JSON
- WebClient with Basic Auth (`email:apiToken` base64-encoded)

## Requirements

- Java 17+
- Spring Boot 3.4+ with WebFlux
- Spring AI 1.0.0+
- spring-ai-reactive-tools 0.2.0+

## License

[MIT License](LICENSE)
