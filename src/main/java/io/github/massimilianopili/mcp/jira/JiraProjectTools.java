package io.github.massimilianopili.mcp.jira;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.jira.api-token")
public class JiraProjectTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraProjectTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_list_projects",
          description = "Elenca tutti i progetti Jira accessibili all'utente corrente")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listProjects() {
        return webClient.get()
                .uri(props.getRestUrl() + "/project")
                .retrieve()
                .bodyToMono(List.class)
                .map(projects -> ((List<Map<String, Object>>) projects).stream()
                        .map(p -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", p.getOrDefault("id", ""));
                            result.put("key", p.getOrDefault("key", ""));
                            result.put("name", p.getOrDefault("name", ""));
                            result.put("projectTypeKey", p.getOrDefault("projectTypeKey", ""));
                            result.put("style", p.getOrDefault("style", ""));
                            return result;
                        }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista progetti: " + e.getMessage()))));
    }

    @ReactiveTool(name = "jira_get_project",
          description = "Recupera i dettagli di un progetto Jira per chiave (es. MCP)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getProject(
            @ToolParam(description = "Chiave del progetto, es: MCP") String projectKey) {
        return webClient.get()
                .uri(props.getRestUrl() + "/project/" + projectKey)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero progetto " + projectKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_list_issue_types",
          description = "Elenca tutti i tipi di issue disponibili nell'istanza Jira (Story, Task, Bug, Epic, Sub-task, ecc.)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listIssueTypes() {
        return webClient.get()
                .uri(props.getRestUrl() + "/issuetype")
                .retrieve()
                .bodyToMono(List.class)
                .map(types -> ((List<Map<String, Object>>) types).stream()
                        .map(t -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", t.getOrDefault("id", ""));
                            result.put("name", t.getOrDefault("name", ""));
                            result.put("description", t.getOrDefault("description", ""));
                            result.put("subtask", t.getOrDefault("subtask", false));
                            return result;
                        }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista tipi issue: " + e.getMessage()))));
    }

    @ReactiveTool(name = "jira_list_priorities",
          description = "Elenca le priorita' disponibili (Highest, High, Medium, Low, Lowest)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listPriorities() {
        return webClient.get()
                .uri(props.getRestUrl() + "/priority")
                .retrieve()
                .bodyToMono(List.class)
                .map(priorities -> ((List<Map<String, Object>>) priorities).stream()
                        .map(p -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", p.getOrDefault("id", ""));
                            result.put("name", p.getOrDefault("name", ""));
                            result.put("description", p.getOrDefault("description", ""));
                            return result;
                        }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista priorita': " + e.getMessage()))));
    }

    @ReactiveTool(name = "jira_list_statuses",
          description = "Elenca tutti gli stati disponibili nell'istanza Jira (To Do, In Progress, Done, ecc.)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listStatuses() {
        return webClient.get()
                .uri(props.getRestUrl() + "/status")
                .retrieve()
                .bodyToMono(List.class)
                .map(statuses -> ((List<Map<String, Object>>) statuses).stream()
                        .map(s -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", s.getOrDefault("id", ""));
                            result.put("name", s.getOrDefault("name", ""));
                            result.put("description", s.getOrDefault("description", ""));
                            Object cat = s.get("statusCategory");
                            if (cat instanceof Map) {
                                result.put("category", ((Map<String, Object>) cat).getOrDefault("name", ""));
                            }
                            return result;
                        }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista stati: " + e.getMessage()))));
    }
}
