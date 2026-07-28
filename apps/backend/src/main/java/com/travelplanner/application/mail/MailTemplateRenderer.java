package com.travelplanner.application.mail;

import com.travelplanner.domain.valueobject.MailMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link MailTemplate} plus a model into a {@link MailMessage}.
 *
 * <p><strong>Deliberately not a template engine.</strong> Thymeleaf or Freemarker would be a new
 * runtime dependency, a new attack surface, and a new expression language inside files that contain
 * password-reset links — for a set of seven fixed messages whose only variables are a link and a
 * name. The substitution here is {@code {{key}}} → value, and nothing else is executable.
 *
 * <h2>Escaping</h2>
 *
 * <p>Values are HTML-escaped in the HTML body and inserted verbatim in the text body. That matters
 * for one field in particular: a username is user-chosen, arrives from a sign-up form, and is echoed
 * back in the "that username is taken" mail. Without escaping, registering the username
 * {@code <img src=x onerror=...>} would put script into a mail sent to a third party.
 *
 * <p>Links are escaped too. They are built from a configured base URL and a base64url token, so
 * neither can contain a quote today — but "cannot contain" is a property of code somebody may
 * change, and escaping costs nothing.
 *
 * <h2>Failure</h2>
 *
 * <p>A missing template file throws at render time rather than being skipped. A silently unsent
 * verification mail is indistinguishable, from the user's side, from a broken sign-up; a loud
 * failure is caught by {@link MailDispatcher} and logged as a mail failure, which is visible.
 */
@Component
public class MailTemplateRenderer {

    private static final String TEMPLATE_ROOT = "mail/templates/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z0-9_]+)}}");

    /**
     * @param model placeholder name → value. A placeholder with no model entry is a template bug and
     *        fails loudly rather than rendering "{{reset_link}}" into somebody's inbox.
     */
    public MailMessage render(MailTemplate template, String recipient, Map<String, String> model) {
        return new MailMessage(recipient, template.subject(),
                substitute(load(template.key() + ".html"), model, true),
                substitute(load(template.key() + ".txt"), model, false));
    }

    private static String substitute(String body, Map<String, String> model, boolean escapeHtml) {
        Matcher matcher = PLACEHOLDER.matcher(body);
        StringBuilder rendered = new StringBuilder(body.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = model.get(name);
            if (value == null) {
                throw new IllegalStateException("Mail template placeholder '" + name
                        + "' has no value — the template and its caller have drifted apart");
            }
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(escapeHtml ? escape(value) : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String load(String fileName) {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_ROOT + fileName);
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            throw new UncheckedIOException("Mail template " + TEMPLATE_ROOT + fileName
                    + " is missing from the classpath", missing);
        }
    }

    /**
     * The five characters that can break out of HTML text or an attribute value. Hand-written rather
     * than pulled from a web layer: this class must stay usable from a background job that has no
     * servlet context (§4.0.10 lists a research-complete mail for exactly that case).
     */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
