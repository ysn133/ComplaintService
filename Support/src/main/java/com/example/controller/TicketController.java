import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@ManagedBean
@ViewScoped
public class TicketController {
    private String title;
    private String description;
    private String category;
    private String content;
    private String errorMessage;

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void submit() {
        try {
            // Create JSON payload
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(
                new TicketPayload(title, description, category, content)
            );

            // Create HTTP client
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost/tisk.php"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            // Send request and get response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                // Success case: Redirect to the specified URL
                errorMessage = null; // Clear any previous errors
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect("http://localhost/tickets/success");
            } else {
                // Error case: Stay on the same page
                errorMessage = "Failed to create ticket: " + response.body();
            }
        } catch (IOException | InterruptedException e) {
            // Error case: Stay on the same page
            errorMessage = "Error connecting to the API: " + e.getMessage();
        }
    }

    // Helper class for JSON payload
    private static class TicketPayload {
        private String title;
        private String description;
        private String category;
        private String content;

        public TicketPayload(String title, String description, String category, String content) {
            this.title = title;
            this.description = description;
            this.category = category;
            this.content = content;
        }

        // Getters and setters for JSON serialization
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}