package org.emb.accessrequests.error;

/** The contract's error body: {@code { "error": { "code": "...", "message": "..." } }}. */
public record ErrorResponseBody(Detail error) {

    public record Detail(String code, String message) {
    }

    public static ErrorResponseBody of(String code, String message) {
        return new ErrorResponseBody(new Detail(code, message));
    }
}
