package me.luucka.mangashelf.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Client-side routes (no dot in the last segment, not /api or /actuator) get the app shell. Files
 * and /api/** are untouched. shortcut: routes up to 3 segments deep, extend if the SPA gets deeper.
 */
@Controller
class SpaController {

  private static final String NOT_API = "(?!api$|actuator$)[^.]*";
  private static final String SEGMENT = "[^.]*";

  @GetMapping({
    "/{a:" + NOT_API + "}",
    "/{a:" + NOT_API + "}/{b:" + SEGMENT + "}",
    "/{a:" + NOT_API + "}/{b:" + SEGMENT + "}/{c:" + SEGMENT + "}"
  })
  String shell() {
    return "forward:/index.html";
  }
}
