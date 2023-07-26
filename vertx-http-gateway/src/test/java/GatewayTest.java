import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayMainVerticle;
import io.vertx.core.Vertx;

public class GatewayTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new VertxHttpGatewayMainVerticle());
    }
}
