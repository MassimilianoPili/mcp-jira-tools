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
public class JiraSprintTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraSprintTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_list_sprints",
          description = "Elenca gli sprint di una board Jira. Filtrabile per stato (future, active, closed).")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listSprints(
            @ToolParam(description = "ID numerico della board") int boardId,
            @ToolParam(description = "Stato sprint: future, active, closed (tutti se omesso)", required = false)
            String state) {

        String uri = props.getAgileUrl() + "/board/" + boardId + "/sprint";
        if (state != null && !state.isBlank()) {
            uri += "?state=" + state;
        }

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> result = new LinkedHashMap<>();

                    List<Map<String, Object>> sprints = (List<Map<String, Object>>)
                            response.getOrDefault("values", List.of());
                    result.put("total", sprints.size());
                    result.put("sprints", sprints.stream().map(s -> {
                        Map<String, Object> sprint = new LinkedHashMap<>();
                        sprint.put("id", s.getOrDefault("id", ""));
                        sprint.put("name", s.getOrDefault("name", ""));
                        sprint.put("state", s.getOrDefault("state", ""));
                        sprint.put("startDate", s.getOrDefault("startDate", ""));
                        sprint.put("endDate", s.getOrDefault("endDate", ""));
                        sprint.put("completeDate", s.getOrDefault("completeDate", ""));
                        sprint.put("goal", s.getOrDefault("goal", ""));
                        return sprint;
                    }).toList());
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore lista sprint: " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_sprint",
          description = "Recupera i dettagli di uno sprint per ID")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getSprint(
            @ToolParam(description = "ID numerico dello sprint") int sprintId) {
        return webClient.get()
                .uri(props.getAgileUrl() + "/sprint/" + sprintId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero sprint " + sprintId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_sprint_issues",
          description = "Recupera le issue associate a uno sprint specifico")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getSprintIssues(
            @ToolParam(description = "ID numerico dello sprint") int sprintId,
            @ToolParam(description = "Indice di partenza (default 0)", required = false) Integer startAt,
            @ToolParam(description = "Numero massimo risultati (default 50)", required = false) Integer maxResults) {

        StringBuilder uri = new StringBuilder(props.getAgileUrl() + "/sprint/" + sprintId + "/issue?");
        if (startAt != null) uri.append("startAt=").append(startAt).append("&");
        if (maxResults != null) uri.append("maxResults=").append(Math.min(maxResults, 100)).append("&");

        return webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore issue sprint " + sprintId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_backlog_issues",
          description = "Recupera le issue nel backlog di una board (non assegnate a nessuno sprint)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getBacklogIssues(
            @ToolParam(description = "ID numerico della board") int boardId,
            @ToolParam(description = "Indice di partenza (default 0)", required = false) Integer startAt,
            @ToolParam(description = "Numero massimo risultati (default 50)", required = false) Integer maxResults) {

        StringBuilder uri = new StringBuilder(props.getAgileUrl() + "/board/" + boardId + "/backlog?");
        if (startAt != null) uri.append("startAt=").append(startAt).append("&");
        if (maxResults != null) uri.append("maxResults=").append(Math.min(maxResults, 100)).append("&");

        return webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore backlog board " + boardId + ": " + e.getMessage())));
    }
}
