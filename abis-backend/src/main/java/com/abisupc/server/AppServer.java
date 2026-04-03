package com.abisupc.server;

import com.abisupc.controller.TestController;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class AppServer {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/", Location.CLASSPATH);
            config.bundledPlugins.enableCors(cors ->
                cors.addRule(it -> it.anyHost())
            );
        }).start(7000);

        TestController.register(app);

        System.out.println("ABIS Backend en http://localhost:7000");
    }
}
