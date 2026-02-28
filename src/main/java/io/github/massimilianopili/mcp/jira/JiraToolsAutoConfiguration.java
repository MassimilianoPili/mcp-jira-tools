package io.github.massimilianopili.mcp.jira;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.jira.api-token")
@EnableConfigurationProperties(JiraProperties.class)
@Import({JiraConfig.class,
         JiraIssueTools.class, JiraProjectTools.class,
         JiraBoardTools.class, JiraSprintTools.class,
         JiraCommentTools.class, JiraUserTools.class})
public class JiraToolsAutoConfiguration {
    // Nessun ToolCallbackProvider bean necessario.
    // I tool @ReactiveTool vengono auto-registrati da
    // ReactiveToolAutoConfiguration di spring-ai-reactive-tools.
}
