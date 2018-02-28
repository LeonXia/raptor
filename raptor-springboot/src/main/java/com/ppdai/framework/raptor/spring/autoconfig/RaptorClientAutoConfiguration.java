package com.ppdai.framework.raptor.spring.autoconfig;

import com.ppdai.framework.raptor.filter.refer.ReferFilter;
import com.ppdai.framework.raptor.refer.ReferProxyBuilder;
import com.ppdai.framework.raptor.refer.client.ApacheHttpClient;
import com.ppdai.framework.raptor.refer.client.Client;
import com.ppdai.framework.raptor.refer.repository.UrlRepository;
import com.ppdai.framework.raptor.spring.properties.ApacheHttpClientProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration
@EnableConfigurationProperties({ApacheHttpClientProperties.class})
@Import({RaptorClientPostProcessor.class})
public class RaptorClientAutoConfiguration implements EnvironmentAware {

    private Environment environment;

    private final static String DEFAULT_PREFIX = "raptor.url.";
    private final static String PREFIX_KEY = "raptor.url.prefix";

    @Autowired
    private ApacheHttpClientProperties apacheHttpClientProperties;

    @Bean
    @ConditionalOnProperty(name = "raptor.urlRepository", havingValue = "springEnv", matchIfMissing = true)
    public UrlRepository createUrlRepository() {
        SpringEnvUrlRepository springEnvUrlRepository = new SpringEnvUrlRepository(environment);
        springEnvUrlRepository.setKeyPrefix(environment.getProperty(PREFIX_KEY, DEFAULT_PREFIX));
        return springEnvUrlRepository;
    }

    @Bean
    @ConditionalOnMissingBean
    public Client createDefaultClient() {
        ApacheHttpClient client = new ApacheHttpClient();
        BeanUtils.copyProperties(apacheHttpClientProperties, client);
        client.init();
        return client;
    }

    @Bean
    public ReferProxyBuilder createReferProxyBuilder(Client client, ObjectProvider<List<ReferFilter>> referFilters) {
        return ReferProxyBuilder.newBuilder().addFilters(referFilters.getIfAvailable()).client(client);
    }

    @Bean
    public RaptorClientRegistry createClientRegistry(UrlRepository urlRepository, ReferProxyBuilder referProxyBuilder) {
        return new RaptorClientRegistry(urlRepository, referProxyBuilder);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
