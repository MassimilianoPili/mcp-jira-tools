package io.github.massimilianopili.mcp.jira;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "mcp.jira.api-token")
public class JiraConfig {

    @Bean(name = "jiraWebClient")
    public WebClient jiraWebClient(JiraProperties props) {
        String credentials = Base64.getEncoder()
                .encodeToString((props.getEmail() + ":" + props.getApiToken()).getBytes());

        return WebClient.builder()
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Accept", "application/json")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                        .build())
                .build();
    }
}
