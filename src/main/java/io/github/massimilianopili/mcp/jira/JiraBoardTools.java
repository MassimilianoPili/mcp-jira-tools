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
public class JiraBoardTools {

    private final WebClient webClient;
    private final JiraProperties props;

    public JiraBoardTools(
            @Qualifier("jiraWebClient") WebClient webClient,
            JiraProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "jira_list_boards",
          description = "Elenca le board Jira (Scrum e Kanban). Filtrabile per nome o progetto.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listBoards(
            @ToolParam(description = "Filtra per nome board (parziale)", required = false) String name,
            @ToolParam(description = "Filtra per chiave progetto", required = false) String projectKeyOrId,
            @ToolParam(description = "Tipo board: scrum, kanban, simple", required = false) String type) {

        StringBuilder uri = new StringBuilder(props.getAgileUrl() + "/board?");
        if (name != null && !name.isBlank()) {
            uri.append("name=").append(name).append("&");
        }
        if (projectKeyOrId != null && !projectKeyOrId.isBlank()) {
            uri.append("projectKeyOrId=").append(projectKeyOrId).append("&");
        }
        if (type != null && !type.isBlank()) {
            uri.append("type=").append(type).append("&");
        }

        return webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("total", response.getOrDefault("total", 0));

                    List<Map<String, Object>> boards = (List<Map<String, Object>>)
                            response.getOrDefault("values", List.of());
                    result.put("boards", boards.stream().map(b -> {
                        Map<String, Object> board = new LinkedHashMap<>();
                        board.put("id", b.getOrDefault("id", ""));
                        board.put("name", b.getOrDefault("name", ""));
                        board.put("type", b.getOrDefault("type", ""));
                        Object loc = b.get("location");
                        if (loc instanceof Map) {
                            Map<String, Object> location = (Map<String, Object>) loc;
                            board.put("projectKey", location.getOrDefault("projectKey", ""));
                            board.put("projectName", location.getOrDefault("displayName", ""));
                        }
                        return board;
                    }).toList());
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore lista board: " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_board",
          description = "Recupera i dettagli di una board Jira per ID")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getBoard(
            @ToolParam(description = "ID numerico della board") int boardId) {
        return webClient.get()
                .uri(props.getAgileUrl() + "/board/" + boardId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero board " + boardId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "jira_get_board_configuration",
          description = "Recupera la configurazione di una board: colonne, filtro, stima, ranking")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getBoardConfiguration(
            @ToolParam(description = "ID numerico della board") int boardId) {
        return webClient.get()
                .uri(props.getAgileUrl() + "/board/" + boardId + "/configuration")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore configurazione board " + boardId + ": " + e.getMessage())));
    }
}
