package me.luucka.mangashelf.common;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Keeps DNS validation on the connection path, with normal TLS verification. */
@Configuration(proxyBeanMethods = false)
public class CoverHttpConfiguration {

    // Spring closes the client and its pool when the application stops.
    @Bean(destroyMethod = "close")
    public CloseableHttpClient coverHttpClient() {
        return create(new PublicAddressesOnly(InetAddress::getAllByName));
    }

    static CloseableHttpClient create(DnsResolver resolver) {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setMaxConnTotal(8)
                .setMaxConnPerRoute(4)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(10))
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(10)).build())
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setHandshakeTimeout(Timeout.ofSeconds(5)).build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(connections)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();
    }

    @FunctionalInterface
    interface Lookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    static final class PublicAddressesOnly implements DnsResolver {
        private final Lookup lookup;

        PublicAddressesOnly(Lookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = lookup.resolve(host);
            if (addresses == null || addresses.length == 0) {
                throw new UnknownHostException("No public address for cover host");
            }
            for (InetAddress address : addresses) {
                if (address == null || CoverStore.isUnsafeAddress(address)) {
                    throw new UnknownHostException("Refused non-public cover destination");
                }
            }
            // No second resolution: these exact addresses are used to connect.
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            resolve(host);
            // Do not perform reverse DNS or replace the host used for TLS/SNI.
            return host;
        }
    }
}
