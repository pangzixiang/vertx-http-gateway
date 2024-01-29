package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.SelfSignedCertificate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalDevHttpsTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        SelfSignedCertificate selfSignedCertificate = SelfSignedCertificate.create();
        HttpServerOptions sslOptions = new HttpServerOptions()
                .setUseAlpn(true)
                .setSsl(true)
                .setKeyCertOptions(selfSignedCertificate.keyCertOptions())
                .setTrustOptions(selfSignedCertificate.trustOptions());
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions()
                .setProxyServerOptions(sslOptions)
                .setListenerServerOptions(sslOptions)
                ;

        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions));
    }
}
