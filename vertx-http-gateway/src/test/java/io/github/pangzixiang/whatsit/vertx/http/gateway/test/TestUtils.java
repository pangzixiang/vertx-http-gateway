package io.github.pangzixiang.whatsit.vertx.http.gateway.test;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClientOptions;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class TestUtils {
    @SneakyThrows
    public static List<Integer> getFreePorts() {
        List<ServerSocket> sockets = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ServerSocket socket = new ServerSocket(0);
            sockets.add(socket);
            result.add(socket.getLocalPort());
        }
        for (ServerSocket socket : sockets) {
            socket.close();
        }
        return result;
    }

    public static Future<VertxHttpGatewayConnector> connectToGateway(Vertx vertx, int port, String name, int gatewayRegisterPort, boolean isSsl) {
        WebSocketClientOptions registerClientOptions = new WebSocketClientOptions()
                .setSsl(isSsl).setTrustAll(true)
                ;
        VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions =
                new VertxHttpGatewayConnectorOptions(name, port, "localhost", gatewayRegisterPort)
                        .setRegisterClientOptions(registerClientOptions);
        VertxHttpGatewayConnector vertxHttpGatewayConnector = new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions);
        return vertxHttpGatewayConnector.connect().map(vertxHttpGatewayConnector);
    }

    public static Future<VertxHttpGatewayConnector> connectToGateway(Vertx vertx, int port, String name, int gatewayRegisterPort) {
        return connectToGateway(vertx, port, name, gatewayRegisterPort, false);
    }
}
