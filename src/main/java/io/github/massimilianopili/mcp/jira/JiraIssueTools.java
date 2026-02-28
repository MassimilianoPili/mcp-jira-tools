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
          description = "Cerca issue Jira con JQL (Jira Query Language). "
                      + "Restituisce issue con campi principali. Massimo 50 risultati per pagina.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> searchIssues(
            @ToolParam(description = "Query JQL, es: project = MCP AND status = 'In Progress' ORDER BY priority DESC")
            String jql,
            @ToolParam(description = "Indice di partenza per paginazione (default 0)", required = false)
            Integer startAt,
            @ToolParam(description = "Numero massimo risultati (default 50, max 100)", required = false)
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
          description = "Recupera una singola issue Jira per chiave (es. MCP-123) con tutti i campi")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getIssue(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey,
            @ToolParam(description = "Campi da espandere: changelog, renderedFields, transitions", required = false)
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
          description = "Crea una nuova issue in Jira (Story, Task, Bug, Epic)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createIssue(
            @ToolParam(description = "Chiave del progetto, es: MCP") String projectKey,
            @ToolParam(description = "Tipo issue: Story, Task, Bug, Epic, Sub-task") String issueType,
            @ToolParam(description = "Titolo/sommario della issue") String summary,
            @ToolParam(description = "Descrizione (testo semplice, verra' convertito in ADF)", required = false)
            String description,
            @ToolParam(description = "Priorita': Highest, High, Medium, Low, Lowest", required = false)
            String priority,
            @ToolParam(description = "Assegnatario (account ID Jira)", required = false)
            String assigneeAccountId,
            @ToolParam(description = "Label separate da virgola", required = false)
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
          description = "Aggiorna i campi di una issue Jira esistente. Specifica solo i campi da modificare.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> updateIssue(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey,
            @ToolParam(description = "Nuovo sommario/titolo", required = false) String summary,
            @ToolParam(description = "Nuova descrizione (testo semplice)", required = false) String description,
            @ToolParam(description = "Nuova priorita'", required = false) String priority,
            @ToolParam(description = "Nuovo assegnatario (account ID)", required = false) String assigneeAccountId,
            @ToolParam(description = "Nuove label (separate da virgola)", required = false) String labels) {
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
          description = "Recupera le transizioni di stato disponibili per una issue (es. To Do -> In Progress -> Done)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getTransitions(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey) {
        return webClient.get()
                .uri(props.getRestUrl() + "/issue/" + issueKey + "/transitions")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero transizioni " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_transition_issue",
          description = "Esegue una transizione di stato su una issue (es. da 'To Do' a 'In Progress'). "
                      + "Usa jira_get_transitions per ottenere gli ID disponibili.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> transitionIssue(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey,
            @ToolParam(description = "ID della transizione (numerico, ottenuto da jira_get_transitions)") String transitionId) {
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
          description = "Elimina una issue Jira. Operazione irreversibile.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> deleteIssue(
            @ToolParam(description = "Chiave della issue da eliminare, es: MCP-123") String issueKey) {
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
