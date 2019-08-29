package com.sample.router;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatesRestAPI extends AbstractVerticle {

  public static final String API_ENDPOINT = "/api";
  public static final String STATES_API_ENDPOINT = "/api/states";

  private Map<String, JsonObject> states = new HashMap<>();

  @Override
  public void start(Future<Void> startFuture) {
    String host = config().getString("http.host", "localhost");
    int port = config().getInteger("http.port", 8080);

    initList();

    Vertx vertxR = Vertx.newInstance(vertx);
    Router router = Router.router(vertxR);

    router.get("/").handler(rc -> {
        rc.response()
        .putHeader("content-type", "text/html")
        .end("Here's StatesListing API");
    });

    router.get(API_ENDPOINT).handler(rc -> {
      rc.response().putHeader("content-type", "application/json")
          .end(new JsonObject().put("name", "statelist").put("version", 1).encode());
    });

    router.route().handler(BodyHandler.create());
    router.post(STATES_API_ENDPOINT).handler(this::addState);
    router.get(STATES_API_ENDPOINT).handler(this::listStates);
    router.get(STATES_API_ENDPOINT + "/:id").handler(this::getStateById);

    vertxR.createHttpServer()
        .requestHandler(router::accept)
        .listen(port, ar -> {
          if (ar.failed()) {
            startFuture.cause().printStackTrace();
          }
        });
  }

  private void initList() {
    addState(new JsonObject().put("id", "s1").put("name", "Dormant"));
    addState(new JsonObject().put("id", "s2").put("name", "PreInit"));
    addState(new JsonObject().put("id", "s3").put("name", "Init"));
    addState(new JsonObject().put("id", "s4").put("name", "Dead"));
  }

  private void addState(JsonObject state) {
    states.putIfAbsent(state.getString("id"), state);
  }

  private void addState(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    JsonObject requestBody = routingContext.getBodyAsJson();
    if (requestBody != null && requestBody.containsKey("name")) {
      String id = requestBody.containsKey("id") ? requestBody.getString("id") : UUID.randomUUID().toString();
      try {
        addState(requestBody);
        response.setStatusCode(HttpResponseStatus.CREATED.code()).end("State added");
        routingContext.response().end();
      } catch (IllegalArgumentException ie) {
        routingContext.fail(500);
      }
    } else {
      response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .end(String.format("Body is %s. 'id' and 'name' should be provided", requestBody));
    }
  }

  private void listStates(RoutingContext routingContext) {
    JsonArray arr = new JsonArray();
    states.forEach((k, v) -> arr.add(v));
    routingContext.response().putHeader("content-type", "application/json").end(arr.encodePrettily());
    routingContext.response().end();
  }

  private void getStateById(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");

    HttpServerResponse response = routingContext.response();
    if (id == null) {
      routingContext.fail(400);
    } else {
      JsonObject state = states.get(id);
      if (state == null) {
        routingContext.fail(404);
      } else {
        response.putHeader("content-type", "application/json").end(state.encodePrettily());
        routingContext.response().end();
      }
    }
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    DeploymentOptions options = new DeploymentOptions()
        .setConfig(new JsonObject()
            .put("http.port", 8081)
            .put("http.host", "localhost")
        );

    vertx.deployVerticle(StatesRestAPI.class.getName(), options);

  }

}
