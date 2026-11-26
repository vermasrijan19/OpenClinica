package org.akaza.openclinica.web.restful;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class RestFallbackController {

    @RequestMapping("/**")
    public ResponseEntity<Void> notFound(HttpServletResponse response) throws Exception {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return null;
    }
}
