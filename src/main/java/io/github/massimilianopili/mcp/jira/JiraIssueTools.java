package io.github.massimilianopili.mcp.jira;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.jira.api-token")
public class JiraIssueTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraIssueTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_search_issues",
          description = "Searches Jira issues with JQL (Jira Query Language). "
                      + "Returns issues with main fields. Maximum 50 results per page.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> searchIssues(
            @ToolParam(description = "JQL query, e.g.: project = MCP AND status = 'In Progress' ORDER BY priority DESC")
            String jql,
            @ToolParam(description = "Start index for pagination (default 0)", required = false)
            Integer startAt,
            @ToolParam(description = "Maximum number of results (default 50, max 100)", required = false)
            Integer maxResults) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jql", jql);
        body.put("startAt", startAt != null ? startAt : 0);
        body.put("maxResults", maxResults != null ? Math.min(maxResults, 100) : 50);
        body.put("fields", List.of(
                "summary", "status", "issuetype", "priority",
                "assignee", "reporter", "created", "updated",
                "labels", "sprint"));

        return webClient.post()
                .uri(props.getRestUrl() + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("total", response.getOrDefault("total", 0));
                    result.put("startAt", response.getOrDefault("startAt", 0));
                    result.put("maxResults", response.getOrDefault("maxResults", 50));

                    List<Map<String, Object>> issues = (List<Map<String, Object>>)
                            response.getOrDefault("issues", List.of());
                    result.put("issues", issues.stream().map(this::extractIssueFields).toList());
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore ricerca issue: " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_issue",
          description = "Retrieves a single Jira issue by key (e.g. MCP-123) with all fields")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getIssue(
            @ToolParam(description = "Issue key, e.g.: MCP-123") String issueKey,
            @ToolParam(description = "Fields to expand: changelog, renderedFields, transitions", required = false)
            String expand) {
        String uri = props.getRestUrl() + "/issue/" + issueKey;
        if (expand != null && !expand.isBlank()) {
            uri += "?expand=" + expand;
        }
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero issue " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_create_issue",
          description = "Creates a new Jira issue (Story, Task, Bug, Epic)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createIssue(
            @ToolParam(description = "Project key, e.g.: MCP") String projectKey,
            @ToolParam(description = "Issue type: Story, Task, Bug, Epic, Sub-task") String issueType,
            @ToolParam(description = "Issue summary/title") String summary,
            @ToolParam(description = "Description (plain text, will be converted to ADF)", required = false)
            String description,
            @ToolParam(description = "Priority: Highest, High, Medium, Low, Lowest", required = false)
            String priority,
            @ToolParam(description = "Assignee (Jira account ID)", required = false)
            String assigneeAccountId,
            @ToolParam(description = "Comma-separated labels", required = false)
            String labels) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("issuetype", Map.of("name", issueType));
        fields.put("summary", summary);

        if (description != null && !description.isBlank()) {
            fields.put("description", toAdf(description));
        }
        if (priority != null && !priority.isBlank()) {
            fields.put("priority", Map.of("name", priority));
        }
        if (assigneeAccountId != null && !assigneeAccountId.isBlank()) {
            fields.put("assignee", Map.of("accountId", assigneeAccountId));
        }
        if (labels != null && !labels.isBlank()) {
            fields.put("labels", Arrays.stream(labels.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList());
        }

        return webClient.post()
                .uri(props.getRestUrl() + "/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("fields", fields))
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione issue: " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_update_issue",
          description = "Updates fields of an existing Jira issue. Only specify the fields to change.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> updateIssue(
            @ToolParam(description = "Issue key, e.g.: MCP-123") String issueKey,
            @ToolParam(description = "New summary/title", required = false) String summary,
            @ToolParam(description = "New description (plain text)", required = false) String description,
            @ToolParam(description = "New priority", required = false) String priority,
            @ToolParam(description = "New assignee (account ID)", required = false) String assigneeAccountId,
            @ToolParam(description = "New labels (comma-separated)", required = false) String labels) {
        return Mono.defer(() -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            if (summary != null && !summary.isBlank()) fields.put("summary", summary);
            if (description != null && !description.isBlank()) fields.put("description", toAdf(description));
            if (priority != null && !priority.isBlank()) fields.put("priority", Map.of("name", priority));
            if (assigneeAccountId != null && !assigneeAccountId.isBlank()) {
                fields.put("assignee", Map.of("accountId", assigneeAccountId));
            }
            if (labels != null && !labels.isBlank()) {
                fields.put("labels", Arrays.stream(labels.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList());
            }

            if (fields.isEmpty()) {
                return Mono.just(Map.<String, Object>of("error", "Nessun campo da aggiornare specificato"));
            }

            return webClient.put()
                    .uri(props.getRestUrl() + "/issue/" + issueKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("fields", fields))
                    .retrieve()
                    .toBodilessEntity()
                    .map(r -> Map.<String, Object>of("status", "ok", "issueKey", issueKey));
        })
        .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiornamento issue " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_transitions",
          description = "Retrieves available status transitions for an issue (e.g. To Do -> In Progress -> Done)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getTransitions(
            @ToolParam(description = "Issue key, e.g.: MCP-123") String issueKey) {
        return webClient.get()
                .uri(props.getRestUrl() + "/issue/" + issueKey + "/transitions")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero transizioni " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_transition_issue",
          description = "Performs a status transition on an issue (e.g. from 'To Do' to 'In Progress'). "
                      + "Use jira_get_transitions to get available IDs.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> transitionIssue(
            @ToolParam(description = "Issue key, e.g.: MCP-123") String issueKey,
            @ToolParam(description = "Transition ID (numeric, obtained from jira_get_transitions)") String transitionId) {
        return webClient.post()
                .uri(props.getRestUrl() + "/issue/" + issueKey + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", "ok", "issueKey", issueKey, "transitionId", transitionId))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore transizione issue " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_delete_issue",
          description = "Deletes a Jira issue. Irreversible operation.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> deleteIssue(
            @ToolParam(description = "Issue key to delete, e.g.: MCP-123") String issueKey) {
        return webClient.delete()
                .uri(props.getRestUrl() + "/issue/" + issueKey)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", "deleted", "issueKey", issueKey))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione issue " + issueKey + ": " + e.getMessage())));
    }

    /** Converte testo semplice in Atlassian Document Format (ADF) */
    private Map<String, Object> toAdf(String text) {
        return Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(
                        Map.of("type", "paragraph",
                                "content", List.of(
                                        Map.of("type", "text", "text", text)
                                )
                        )
                )
        );
    }

    /** Estrae i campi principali da una issue per risposte compatte */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractIssueFields(Map<String, Object> issue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", issue.getOrDefault("key", ""));
        result.put("id", issue.getOrDefault("id", ""));

        Object fieldsObj = issue.get("fields");
        if (fieldsObj instanceof Map) {
            Map<String, Object> fields = (Map<String, Object>) fieldsObj;
            result.put("summary", fields.getOrDefault("summary", ""));
            result.put("created", fields.getOrDefault("created", ""));
            result.put("updated", fields.getOrDefault("updated", ""));

            extractName(fields, "status", result);
            extractName(fields, "issuetype", result);
            extractName(fields, "priority", result);
            extractDisplayName(fields, "assignee", result);
            extractDisplayName(fields, "reporter", result);

            Object labels = fields.get("labels");
            if (labels instanceof List) {
                result.put("labels", labels);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void extractName(Map<String, Object> fields, String key, Map<String, Object> result) {
        Object obj = fields.get(key);
        if (obj instanceof Map) {
            result.put(key, ((Map<String, Object>) obj).getOrDefault("name", ""));
        }
    }

    @SuppressWarnings("unchecked")
    private void extractDisplayName(Map<String, Object> fields, String key, Map<String, Object> result) {
        Object obj = fields.get(key);
        if (obj instanceof Map) {
            result.put(key, ((Map<String, Object>) obj).getOrDefault("displayName", ""));
        }
    }
}
