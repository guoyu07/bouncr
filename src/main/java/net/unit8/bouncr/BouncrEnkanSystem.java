package net.unit8.bouncr;

import enkan.Env;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.builtin.HmacEncoder;
import enkan.component.doma2.DomaProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.freemarker.FreemarkerTemplateEngine;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.metrics.MetricsComponent;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import net.unit8.bouncr.cert.CertificateProvider;
import net.unit8.bouncr.cert.ReloadableTrustManager;
import net.unit8.bouncr.component.BouncrConfiguration;
import net.unit8.bouncr.component.Flake;
import net.unit8.bouncr.component.RealmCache;
import net.unit8.bouncr.component.StoreProvider;
import net.unit8.bouncr.proxy.ReverseProxyComponent;
import net.unit8.bouncr.sign.IdToken;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;
import static enkan.util.ThreadingUtils.some;

/**
 * An EnkanSystem for Bouncr application.
 *
 * @author kawasima
 */
public class BouncrEnkanSystem implements EnkanSystemFactory {
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                "hmac", new HmacEncoder(),
                "config", new BouncrConfiguration(),
                "doma", new DomaProvider(),
                "jackson", new JacksonBeansConverter(),
                "storeprovider", new StoreProvider(),
                "flake", new Flake(),
                "certificate", new CertificateProvider(),
                "trustManager", builder(new ReloadableTrustManager())
                        .set(ReloadableTrustManager::setTruststorePath, Env.getString("TRUSTSTORE_PATH", ""))
                        .set(ReloadableTrustManager::setTruststorePassword, Env.getString("TRUSTSTORE_PASSWORD", ""))
                        .build(),
                "idToken", new IdToken(),
                "realmCache", new RealmCache(),
                "flyway", new FlywayMigration(),
                "template", new FreemarkerTemplateEngine(),
                "metrics", new MetricsComponent(),
                "datasource", new HikariCPComponent(OptionMap.of("uri", "jdbc:h2:mem:test")),
                "app", new ApplicationComponent("net.unit8.bouncr.BouncrApplicationFactory"),
                "http", builder(new ReverseProxyComponent())
                        .set(ReverseProxyComponent::setPort, Env.getInt("PORT", 3000))
                        .set(ReverseProxyComponent::setSslPort, Env.getInt("SSL_PORT", 3002))
                        .set(ReverseProxyComponent::setSsl, Env.get("SSL_PORT") != null)
                        .set(ReverseProxyComponent::setKeystorePath, Env.getString("KEYSTORE_PATH", ""))
                        .set(ReverseProxyComponent::setKeystorePassword, Env.getString("KEYSTORE_PASSWORD", ""))
                        .build()
        ).relationships(
                component("http").using("app", "storeprovider", "realmCache", "trustManager", "config"),
                component("app").using(
                        "storeprovider", "datasource", "template", "doma", "jackson", "metrics",
                        "realmCache", "config", "idToken", "certificate", "trustManager"),
                component("storeprovider").using("config"),
                component("realmCache").using("doma"),
                component("doma").using("datasource", "flyway"),
                component("flyway").using("datasource"),
                component("certificate").using("flake", "config")
        );
    }
}
