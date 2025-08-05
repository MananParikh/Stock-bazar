package frontend.src.main.java.frontend;

import com.fasterxml.jackson.annotation.JsonProperty;

// temporary class, will be removed later on

public class ErrorResponse {
    @JsonProperty("message")
    private String message;

    @JsonProperty("code")
    private int code;


    public ErrorResponse() {}

    public ErrorResponse(String message) {
        this.code = 404;
        this.message = message;
    }

    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
}
