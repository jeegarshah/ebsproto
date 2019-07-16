package com.js;

import org.httpobjects.DSL;
import org.httpobjects.HttpObject;
import org.httpobjects.Request;
import org.httpobjects.Response;
import org.httpobjects.jetty.HttpObjectsJettyHandler;

/**
 * Hello world!
 *
 */
public class App
{
    public static void main( String[] args )
    {
        HttpObjectsJettyHandler.launchServer(8080, new X("/hello"));
    }

    static class X extends HttpObject{

        public X(String pathPattern) {
            super(pathPattern);
        }

        @Override
        public Response get(Request req) {
            return DSL.OK(DSL.Text("hello world"));
        }
    }
}
