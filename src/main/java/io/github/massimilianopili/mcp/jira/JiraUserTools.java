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
public class JiraUserTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraUserTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_get_current_user",
          description = "Recupera le informazioni dell'utente corrente (account ID, email, display name)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getCurrentUser() {
        return webClient.get()
                .uri(props.getRestUrl() + "/myself")
                .retrieve()
                .bodyToMono(Map.class)
                .map(user -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("accountId", user.getOrDefault("accountId", ""));
                    result.put("displayName", user.getOrDefault("displayName", ""));
                    result.put("emailAddress", user.getOrDefault("emailAddress", ""));
                    result.put("active", user.getOrDefault("active", false));
                    result.put("accountType", user.getOrDefault("accountType", ""));
                    result.put("timeZone", user.getOrDefault("timeZone", ""));
                    return (Map<String, Object>) result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero utente corrente: " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_search_users",
          description = "Cerca utenti Jira per nome, email o username. Utile per trovare l'account ID da usare nell'assegnazione issue.")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> searchUsers(
            @ToolParam(description = "Query di ricerca (nome, email o username)") String query,
            @ToolParam(description = "Numero massimo risultati (default 25)", required = false)
            Integer maxResults) {

        String uri = props.getRestUrl() + "/user/search?query=" + query;
        if (maxResults != null) {
            uri += "&maxResults=" + Math.min(maxResults, 50);
        }

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(List.class)
                .map(users -> ((List<Map<String, Object>>) users).stream()
                        .map(u -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("accountId", u.getOrDefault("accountId", ""));
                            result.put("displayName", u.getOrDefault("displayName", ""));
                            result.put("emailAddress", u.getOrDefault("emailAddress", ""));
                            result.put("active", u.getOrDefault("active", false));
                            result.put("accountType", u.getOrDefault("accountType", ""));
                            return result;
                        }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore ricerca utenti: " + e.getMessage()))));
    }
}
