/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ssl;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.ssl.cert.CertificateInfo;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.core.ssl.SSLConfigurationSettings.getKeyStoreType;


/**
 * Represents the configuration for an SSLContext
 */
public final class SSLConfiguration {

    // These settings are never registered, but they exist so that we can parse the values defined under grouped settings. Also, some are
    // implemented as optional settings, which provides a declarative manner for fallback as we typically fallback to values from a
    // different configuration
    static final SSLConfigurationSettings SETTINGS_PARSER = SSLConfigurationSettings.withoutPrefix();

    private final KeyConfig keyConfig;
    private final TrustConfig trustConfig;
    private final List<String> ciphers;
    private final List<String> supportedProtocols;
    private final SSLClientAuth sslClientAuth;
    private final VerificationMode verificationMode;

    /**
     * Creates a new SSLConfiguration from the given settings. There is no fallback configuration when invoking this constructor so
     * un-configured aspects will take on their default values.
     *
     * @param settings the SSL specific settings; only the settings under a *.ssl. prefix
     */
    SSLConfiguration(Settings settings) {
        this.keyConfig = createKeyConfig(settings, (SSLConfiguration) null);
        this.trustConfig = createTrustConfig(settings, keyConfig, null);
        this.ciphers = getListOrDefault(SETTINGS_PARSER.ciphers, settings, XPackSettings.DEFAULT_CIPHERS);
        this.supportedProtocols = getListOrDefault(SETTINGS_PARSER.supportedProtocols, settings, XPackSettings.DEFAULT_SUPPORTED_PROTOCOLS);
        this.sslClientAuth = SETTINGS_PARSER.clientAuth.get(settings).orElse(XPackSettings.CLIENT_AUTH_DEFAULT);
        this.verificationMode = SETTINGS_PARSER.verificationMode.get(settings).orElse(XPackSettings.VERIFICATION_MODE_DEFAULT);
    }

    /**
     * Creates a new SSLConfiguration from the given settings and global/default SSLConfiguration. If the settings do not contain a value
     * for a given aspect, the value from the global configuration will be used.
     *
     * @param settings               the SSL specific settings; only the settings under a *.ssl. prefix
     * @param globalSSLConfiguration the default configuration that is used as a fallback
     */
    SSLConfiguration(Settings settings, SSLConfiguration globalSSLConfiguration) {
        Objects.requireNonNull(globalSSLConfiguration);
        this.keyConfig = createKeyConfig(settings, globalSSLConfiguration);
        this.trustConfig = createTrustConfig(settings, keyConfig, globalSSLConfiguration);
        this.ciphers = getListOrDefault(SETTINGS_PARSER.ciphers, settings, globalSSLConfiguration.cipherSuites());
        this.supportedProtocols = getListOrDefault(SETTINGS_PARSER.supportedProtocols, settings,
                globalSSLConfiguration.supportedProtocols());
        this.sslClientAuth = SETTINGS_PARSER.clientAuth.get(settings).orElse(globalSSLConfiguration.sslClientAuth());
        this.verificationMode = SETTINGS_PARSER.verificationMode.get(settings).orElse(globalSSLConfiguration.verificationMode());
    }

    /**
     * The configuration for the key, if any, that will be used as part of this ssl configuration
     */
    KeyConfig keyConfig() {
        return keyConfig;
    }

    /**
     * The configuration of trust material that will be used as part of this ssl configuration
     */
    TrustConfig trustConfig() {
        return trustConfig;
    }

    /**
     * The cipher suites that will be used for this ssl configuration
     */
    List<String> cipherSuites() {
        return ciphers;
    }

    /**
     * The protocols that are supported by this configuration
     */
    List<String> supportedProtocols() {
        return supportedProtocols;
    }

    /**
     * The verification mode for this configuration; this mode controls certificate and hostname verification
     */
    public VerificationMode verificationMode() {
        return verificationMode;
    }

    /**
     * The client auth configuration
     */
    SSLClientAuth sslClientAuth() {
        return sslClientAuth;
    }

    /**
     * Provides the list of paths to files that back this configuration
     */
    List<Path> filesToMonitor(@Nullable Environment environment) {
        if (keyConfig() == trustConfig()) {
            return keyConfig().filesToMonitor(environment);
        }
        List<Path> paths = new ArrayList<>(keyConfig().filesToMonitor(environment));
        paths.addAll(trustConfig().filesToMonitor(environment));
        return paths;
    }

    @Override
    public String toString() {
        return "SSLConfiguration{" +
                "keyConfig=[" + keyConfig +
                "], trustConfig=" + trustConfig +
                "], cipherSuites=[" + ciphers +
                "], supportedProtocols=[" + supportedProtocols +
                "], sslClientAuth=[" + sslClientAuth +
                "], verificationMode=[" + verificationMode +
                "]}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SSLConfiguration)) return false;

        SSLConfiguration that = (SSLConfiguration) o;

        if (this.keyConfig() != null ? !this.keyConfig().equals(that.keyConfig()) : that.keyConfig() != null) {
            return false;
        }
        if (this.trustConfig() != null ? !this.trustConfig().equals(that.trustConfig()) : that.trustConfig() != null) {
            return false;
        }
        if (this.cipherSuites() != null ? !this.cipherSuites().equals(that.cipherSuites()) : that.cipherSuites() != null) {
            return false;
        }
        if (!this.supportedProtocols().equals(that.supportedProtocols())) {
            return false;
        }
        if (this.verificationMode() != that.verificationMode()) {
            return false;
        }
        if (this.sslClientAuth() != that.sslClientAuth()) {
            return false;
        }
        return this.supportedProtocols() != null ?
                this.supportedProtocols().equals(that.supportedProtocols()) : that.supportedProtocols() == null;
    }

    @Override
    public int hashCode() {
        int result = this.keyConfig() != null ? this.keyConfig().hashCode() : 0;
        result = 31 * result + (this.trustConfig() != null ? this.trustConfig().hashCode() : 0);
        result = 31 * result + (this.cipherSuites() != null ? this.cipherSuites().hashCode() : 0);
        result = 31 * result + (this.supportedProtocols() != null ? this.supportedProtocols().hashCode() : 0);
        result = 31 * result + this.verificationMode().hashCode();
        result = 31 * result + this.sslClientAuth().hashCode();
        return result;
    }

    private static KeyConfig createKeyConfig(Settings settings, SSLConfiguration global) {
        final String trustStoreAlgorithm = SETTINGS_PARSER.truststoreAlgorithm.get(settings);
        final KeyConfig config = CertParsingUtils.createKeyConfig(SETTINGS_PARSER.x509KeyPair, settings, trustStoreAlgorithm);
        if (config != null) {
            return config;
        }
        if (global != null) {
            return global.keyConfig();
        }
        if (System.getProperty("javax.net.ssl.keyStore") != null) {
            // TODO: we should not support loading a keystore from sysprops...
            try (SecureString keystorePassword = new SecureString(System.getProperty("javax.net.ssl.keyStorePassword", ""))) {
                return new StoreKeyConfig(System.getProperty("javax.net.ssl.keyStore"), "jks", keystorePassword, keystorePassword,
                        System.getProperty("ssl.KeyManagerFactory.algorithm", KeyManagerFactory.getDefaultAlgorithm()),
                        System.getProperty("ssl.TrustManagerFactory.algorithm", TrustManagerFactory.getDefaultAlgorithm()));
            }
        }
        return KeyConfig.NONE;
    }

    private static TrustConfig createTrustConfig(Settings settings, KeyConfig keyConfig, SSLConfiguration global) {
        final TrustConfig trustConfig = createCertChainTrustConfig(settings, keyConfig, global);
        return SETTINGS_PARSER.trustRestrictionsPath.get(settings)
                .map(path -> (TrustConfig) new RestrictedTrustConfig(settings, path, trustConfig))
                .orElse(trustConfig);
    }

    private static TrustConfig createCertChainTrustConfig(Settings settings, KeyConfig keyConfig, SSLConfiguration global) {
        String trustStorePath = SETTINGS_PARSER.truststorePath.get(settings).orElse(null);

        List<String> caPaths = getListOrNull(SETTINGS_PARSER.caPaths, settings);
        if (trustStorePath != null && caPaths != null) {
            throw new IllegalArgumentException("you cannot specify a truststore and ca files");
        }

        VerificationMode verificationMode = SETTINGS_PARSER.verificationMode.get(settings).orElseGet(() -> {
            if (global != null) {
                return global.verificationMode();
            }
            return XPackSettings.VERIFICATION_MODE_DEFAULT;
        });
        if (verificationMode.isCertificateVerificationEnabled() == false) {
            return TrustAllConfig.INSTANCE;
        } else if (caPaths != null) {
            return new PEMTrustConfig(caPaths);
        } else if (trustStorePath != null) {
            SecureString trustStorePassword = SETTINGS_PARSER.truststorePassword.get(settings);
            String trustStoreAlgorithm = SETTINGS_PARSER.truststoreAlgorithm.get(settings);
            String trustStoreType = getKeyStoreType(SETTINGS_PARSER.truststoreType, settings, trustStorePath);
            return new StoreTrustConfig(trustStorePath, trustStoreType, trustStorePassword, trustStoreAlgorithm);
        } else if (global == null && System.getProperty("javax.net.ssl.trustStore") != null) {
            try (SecureString truststorePassword = new SecureString(System.getProperty("javax.net.ssl.trustStorePassword", ""))) {
                return new StoreTrustConfig(System.getProperty("javax.net.ssl.trustStore"), "jks", truststorePassword,
                        System.getProperty("ssl.TrustManagerFactory.algorithm", TrustManagerFactory.getDefaultAlgorithm()));
            }
        } else if (global != null && keyConfig == global.keyConfig()) {
            return global.trustConfig();
        } else if (keyConfig != KeyConfig.NONE) {
            return DefaultJDKTrustConfig.merge(keyConfig);
        } else {
            return DefaultJDKTrustConfig.INSTANCE;
        }
    }

    private static List<String> getListOrNull(Setting<List<String>> listSetting, Settings settings) {
        return getListOrDefault(listSetting, settings, null);
    }

    private static List<String> getListOrDefault(Setting<List<String>> listSetting, Settings settings, List<String> defaultValue) {
        if (listSetting.exists(settings)) {
            return listSetting.get(settings);
        }
        return defaultValue;
    }

    /**
     * Returns information about each certificate that referenced by this SSL configurations.
     * This includes certificates used for identity (with a private key) and those used for trust, but excludes
     * certificates that are provided by the JRE.
     * @see TrustConfig#certificates(Environment)
     */
    List<CertificateInfo> getDefinedCertificates(@Nullable Environment environment) throws GeneralSecurityException, IOException {
        List<CertificateInfo> certificates = new ArrayList<>();
        certificates.addAll(keyConfig.certificates(environment));
        certificates.addAll(trustConfig.certificates(environment));
        return certificates;
    }
}
