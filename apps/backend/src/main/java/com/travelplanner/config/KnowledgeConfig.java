package com.travelplanner.config;

import com.travelplanner.domain.port.KnowledgePort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Applies ADR 010 §3 — "Production: the application fails to start if any {@code Stub*} knowledge
 * adapter is wired" — to the wiring this context actually has.
 *
 * <p>A configuration class of its own rather than another responsibility on {@link MailConfig} or
 * {@link SecurityConfig}: this check is about the knowledge base, and hanging it off a class that
 * exists for mail or for the filter chain would mean it quietly stops running the day somebody
 * makes that class conditional.
 *
 * <h2>Types, not instances</h2>
 *
 * <p>The adapters are inspected by <em>bean definition</em> — {@code allowEagerInit = false} — so
 * asking the question does not construct anything. Injecting {@code ObjectProvider<KnowledgePort>}
 * and streaming it would instantiate {@code PgVectorKnowledgeAdapter} from inside a configuration
 * class's initialisation, ahead of the post-processor that wraps it in its
 * {@code @Transactional(readOnly = true)} proxy. The check would then have silently disabled the
 * read-only transactions it was meant to protect — a validator that breaks the thing it validates
 * is worse than no validator.
 *
 * <p>By the time any singleton is created, every definition is registered (component scanning runs
 * in the bean-factory post-processing phase), so a definitions-only view is complete rather than
 * merely early.
 */
@Configuration
public class KnowledgeConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConfig.class);

    private final Environment environment;
    private final ListableBeanFactory beans;

    public KnowledgeConfig(Environment environment, ListableBeanFactory beans) {
        this.environment = environment;
        this.beans = beans;
    }

    @Override
    public void afterPropertiesSet() {
        boolean sampleSeed = environment.getProperty(
                KnowledgeConfigValidator.SAMPLE_SEED_PROPERTY, Boolean.class, false);
        List<String> adapters = knowledgeAdapterClassNames();

        KnowledgeConfigValidator.validate(environment.getActiveProfiles(), sampleSeed, adapters);

        if (sampleSeed || adapters.stream().anyMatch(KnowledgeConfigValidator::isStubAdapter)) {
            // Stated once, at boot. ADR 010 §3 also requires the response flag and the UI banner;
            // this line is for whoever is reading a log and wondering why a guide cites
            // `stub:sample`.
            log.warn("Travel knowledge is SAMPLE DATA — every fact is illustrative and its "
                    + "source_ref is the reserved 'stub:sample'. Development only; the application "
                    + "refuses to start this way under the '{}' profile.",
                    KnowledgeConfigValidator.PROD_PROFILE);
        }
    }

    /**
     * Simple class names of every {@link KnowledgePort} bean definition, resolved without
     * instantiating one. A definition whose type cannot be determined yet is skipped rather than
     * guessed at — naming it would be a fabricated verdict, which is the failure mode this whole
     * check exists to prevent.
     */
    private List<String> knowledgeAdapterClassNames() {
        List<String> names = new ArrayList<>();
        for (String beanName : beans.getBeanNamesForType(KnowledgePort.class, true, false)) {
            Class<?> type = beans.getType(beanName, false);
            if (type != null) {
                names.add(type.getSimpleName());
            }
        }
        return names;
    }
}
