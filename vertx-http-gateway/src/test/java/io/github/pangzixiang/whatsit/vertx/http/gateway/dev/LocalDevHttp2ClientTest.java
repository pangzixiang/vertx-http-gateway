package io.github.pangzixiang.whatsit.vertx.http.gateway.dev;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class LocalDevHttp2ClientTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        WebSocketClientOptions registerClientOptions = new WebSocketClientOptions()
                .setSsl(true).setTrustAll(true)
//                .setUseAlpn(true)
                ;
        VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions =
                new VertxHttpGatewayConnectorOptions("grafana", 13000, "localhost", 9090)
                        .setServiceHost("192.168.50.50")
                        .setRegisterClientOptions(registerClientOptions)
                        .setProxyClientOptions(new HttpClientOptions()
                                        .setUseAlpn(true)
                                        .setProtocolVersion(HttpVersion.HTTP_2)
//                                .setProtocolVersion(HttpVersion.HTTP_2)
                        )
                ;

        VertxHttpGatewayConnector vertxHttpGatewayConnector = new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions);
        vertxHttpGatewayConnector.connect();
    }
}
