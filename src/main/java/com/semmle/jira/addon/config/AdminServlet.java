package com.semmle.jira.addon.config;

import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import java.io.IOException;
import java.net.URI;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Scanned
public class AdminServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  @ComponentImport private final UserManager userManager;
  @ComponentImport private final LoginUriProvider loginUriProvider;
  @ComponentImport private final TemplateRenderer renderer;
  @ComponentImport private final PageBuilderService pageBuilderService;

  @Inject
  public AdminServlet(
      UserManager userManager,
      LoginUriProvider loginUriProvider,
      TemplateRenderer renderer,
      PageBuilderService pageBuilderService) {
    this.userManager = userManager;
    this.loginUriProvider = loginUriProvider;
    this.renderer = renderer;
    this.pageBuilderService = pageBuilderService;
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    String username = userManager.getRemoteUsername(request);
    if (username == null || !userManager.isSystemAdmin(username)) {
      redirectToLogin(request, response);
      return;
    }

    pageBuilderService
        .assembler()
        .resources()
        .requireWebResource("com.semmle.lgtm-jira-addon:lgtm-addon-resources");
    response.setContentType("text/html;charset=utf-8");
    renderer.render("admin.vm", response.getWriter());
  }

  private void redirectToLogin(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
  }

  private URI getUri(HttpServletRequest request) {
    StringBuffer builder = request.getRequestURL();
    if (request.getQueryString() != null) {
      builder.append("?");
      builder.append(request.getQueryString());
    }
    return URI.create(builder.toString());
  }
}
