package org.emb.accessrequests.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default {@code /error} endpoint. Errors that happen outside
 * Spring MVC (in the servlet container or a filter) are forwarded here, and
 * still get the contract's JSON body instead of Boot's default one.
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErrorResponseBody> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatusCode status = code instanceof Integer value && value >= 400
                ? HttpStatusCode.valueOf(value)
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ErrorResponseBody.of(ErrorCodes.codeFor(status), ErrorCodes.messageFor(status)));
    }
}
