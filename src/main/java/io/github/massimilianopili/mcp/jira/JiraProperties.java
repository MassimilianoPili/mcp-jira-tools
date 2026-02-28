package io.github.massimilianopili.mcp.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.jira")
public class JiraProperties {

    private String baseUrl;
    private String email;
    private String apiToken;
    private String apiVersion = "3";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    /** URL base REST API: https://myorg.atlassian.net/rest/api/3 */
    public String getRestUrl() {
        return baseUrl + "/rest/api/" + apiVersion;
    }

    /** URL base Agile API: https://myorg.atlassian.net/rest/agile/1.0 */
    public String getAgileUrl() {
        return baseUrl + "/rest/agile/1.0";
    }
}
