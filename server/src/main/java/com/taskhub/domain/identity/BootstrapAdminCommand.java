package com.taskhub.domain.identity;

import com.taskhub.TaskHubApplication;
import org.springframework.boot.*;

/** 受控运维入口；没有HTTP注册路由，不输出任何凭据。 */
public final class BootstrapAdminCommand {
  private BootstrapAdminCommand() {}

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(TaskHubApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAdditionalProfiles("bootstrap-admin");
    try (var context = application.run(args)) {
      var env = context.getEnvironment();
      context
          .getBean(BootstrapAdminService.class)
          .initialize(
              env.getProperty("BOOTSTRAP_ADMIN_USERNAME"),
              env.getProperty("BOOTSTRAP_ADMIN_PASSWORD"),
              env.getProperty("BOOTSTRAP_ADMIN_NAME"),
              env.getProperty("BOOTSTRAP_ADMIN_PHONE"));
    }
  }
}
