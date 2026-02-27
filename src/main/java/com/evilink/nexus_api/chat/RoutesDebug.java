package com.evilink.nexus_api.chat;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class RoutesDebug {

  @Bean
  ApplicationRunner printMappings(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping
  ) {
    return args -> {
      System.out.println("==== REGISTERED MVC ROUTES ====");
      mapping.getHandlerMethods().forEach((info, method) -> {
        var patterns = info.getPatternValues();
        var methods = info.getMethodsCondition().getMethods();
        if (patterns.stream().anyMatch(p -> p.startsWith("/v1"))) {
          System.out.println(methods + " " + patterns + " -> " + method);
        }
      });
      System.out.println("==============================");
    };
  }
}