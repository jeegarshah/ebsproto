package com.js

import org.httpobjects.{DSL, HttpObject, Request, Response}
import org.httpobjects.jetty.HttpObjectsJettyHandler

object ScalaMain extends App {

    HttpObjectsJettyHandler.launchServer(5000, new HelloResource)

}

class HelloResource extends HttpObject("/hello") {
    override def get(req: Request): Response = {
        DSL.OK(DSL.Text("hello from scala"))
    }
}
