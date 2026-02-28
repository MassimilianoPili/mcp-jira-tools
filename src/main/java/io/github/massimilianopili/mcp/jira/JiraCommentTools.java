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
public class JiraCommentTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraCommentTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_list_comments",
          description = "Elenca i commenti di una issue Jira con autore, data e corpo del testo")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listComments(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey) {
        return webClient.get()
                .uri(props.getRestUrl() + "/issue/" + issueKey + "/comment")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("total", response.getOrDefault("total", 0));

                    List<Map<String, Object>> comments = (List<Map<String, Object>>)
                            response.getOrDefault("comments", List.of());
                    result.put("comments", comments.stream().map(c -> {
                        Map<String, Object> comment = new LinkedHashMap<>();
                        comment.put("id", c.getOrDefault("id", ""));
                        comment.put("created", c.getOrDefault("created", ""));
                        comment.put("updated", c.getOrDefault("updated", ""));

                        Object author = c.get("author");
                        if (author instanceof Map) {
                            comment.put("author", ((Map<String, Object>) author)
                                    .getOrDefault("displayName", ""));
                        }

                        Object body = c.get("body");
                        if (body instanceof Map) {
                            comment.put("body", extractTextFromAdf((Map<String, Object>) body));
                        }
                        return comment;
                    }).toList());
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore lista commenti " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_add_comment",
          description = "Aggiunge un commento a una issue Jira")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> addComment(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey,
            @ToolParam(description = "Testo del commento") String commentText) {

        Map<String, Object> body = Map.of(
                "body", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(
                                Map.of("type", "paragraph",
                                        "content", List.of(
                                                Map.of("type", "text", "text", commentText)
                                        ))
                        )
                )
        );

        return webClient.post()
                .uri(props.getRestUrl() + "/issue/" + issueKey + "/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", r.getOrDefault("id", ""));
                    result.put("issueKey", issueKey);
                    result.put("status", "created");
                    return (Map<String, Object>) result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiunta commento a " + issueKey + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_changelog",
          description = "Recupera lo storico delle modifiche di una issue (chi ha cambiato cosa e quando)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getChangelog(
            @ToolParam(description = "Chiave della issue, es: MCP-123") String issueKey,
            @ToolParam(description = "Indice di partenza (default 0)", required = false) Integer startAt,
            @ToolParam(description = "Numero massimo risultati (default 50)", required = false) Integer maxResults) {

        StringBuilder uri = new StringBuilder(props.getRestUrl() + "/issue/" + issueKey + "/changelog?");
        if (startAt != null) uri.append("startAt=").append(startAt).append("&");
        if (maxResults != null) uri.append("maxResults=").append(Math.min(maxResults, 100)).append("&");

        return webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore changelog " + issueKey + ": " + e.getMessage())));
    }

    /** Estrae il testo da un documento ADF (Atlassian Document Format) in modo ricorsivo */
    @SuppressWarnings("unchecked")
    private String extractTextFromAdf(Map<String, Object> adfNode) {
        StringBuilder sb = new StringBuilder();
        Object content = adfNode.get("content");
        if (content instanceof List) {
            for (Object item : (List<Object>) content) {
                if (item instanceof Map) {
                    Map<String, Object> node = (Map<String, Object>) item;
                    String type = (String) node.getOrDefault("type", "");
                    if ("text".equals(type)) {
                        sb.append(node.getOrDefault("text", ""));
                    } else {
                        sb.append(extractTextFromAdf(node));
                    }
                }
            }
        }
        return sb.toString();
    }
}
