package io.github.pangzixiang.whatsit.vertx.http.gateway.dev;

import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayMainVerticle;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.SelfSignedCertificate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalDevHttpsTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Buffer trustStoreBuffer = vertx.fileSystem().readFileBlocking("vertx-gateway.truststore");


        SelfSignedCertificate selfSignedCertificate = SelfSignedCertificate.create();
        HttpServerOptions sslOptions = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(selfSignedCertificate.keyCertOptions())
                .setTrustOptions(selfSignedCertificate.trustOptions())
                .setClientAuth(ClientAuth.REQUIRED)
                .setTrustStoreOptions(new JksOptions().setValue(trustStoreBuffer).setPassword("testtest"));

        HttpServerOptions sslOptions2 = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(selfSignedCertificate.keyCertOptions())
                .setTrustOptions(selfSignedCertificate.trustOptions())
                .setUseAlpn(true)
                .setLogActivity(true);
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions()
                .setListenerServerOptions(sslOptions)
                .setProxyServerOptions(sslOptions2)
                ;

        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions));
    }
}
