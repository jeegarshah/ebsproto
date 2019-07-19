package com.js

import java.io.{BufferedOutputStream, ByteArrayOutputStream, OutputStream}

import com.js.RateType.RateType
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import org.httpobjects.DSL.{Html, OK}
import org.httpobjects.jetty.HttpObjectsJettyHandler
import org.httpobjects.{DSL, HttpObject, Request, Response}
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.parser.QueryParser
import sangria.schema._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


object RateType extends Enumeration {
    type RateType = Value
    val PERCENT, FIXED = Value
}

case class Situation(id: Long, name: String)
case class Consequence(id: Long, rate: BigDecimal, rateType: RateType)
case class SituationConsequences(id: Long, situation: Situation, consequence: Consequence)
case class ActionTerms(id: Long, programTermsId: Long, situationConsequences: List[SituationConsequences])
case class ProgramTerms(id: Long, actionTerms: List[ActionTerms])

trait ProgramTermsService {
    val programTermsDb: Map[Long, ProgramTerms] = Map(
        1L -> ProgramTerms(1, List(
                ActionTerms(100, 1, List(
                    SituationConsequences(
                        110,
                        Situation(120, "Situation120"),
                        Consequence(130, 1.0, RateType.FIXED)
                    ))))),
        2L -> ProgramTerms(2, List(
                ActionTerms(200, 2, List(
                    SituationConsequences(
                        210,
                        Situation(220, "Situation220"),
                        Consequence(230, .02, RateType.PERCENT)
                    )))))
    )

    def getProgramTermsById(id: Option[Long]): Future[ProgramTerms] = {
        id
            .map(
                programTermsDb.get(_)
                    .map(Future.successful)
                    .getOrElse(Future.failed(new RuntimeException("inner"))))
            .getOrElse(Future.failed(new RuntimeException("outer")))
    }

    def getProgramTerms(): Future[List[ProgramTerms]] = {
        Future.successful(programTermsDb.values.toList)
    }
}

trait SituationService {
    val situationsDb: Map[Long, Situation] = Map(
        120L -> Situation(120, "Situation120"),
        220L -> Situation(220, "Situation220")
    )

    def getSituations(): Future[List[Situation]] = {
        Future.successful(situationsDb.values.toList)
    }

    def getSituationsById(id: Option[Long]): Future[Situation] = {
        id.map(
            situationsDb.get(_)
                .map(Future.successful)
                .getOrElse(Future.failed(new RuntimeException("inner"))))
            .getOrElse(Future.failed(new RuntimeException("outer")))
    }
}

object  ProgramTermsApp extends App {
    implicit val RateTypeType = deriveEnumType[RateType.Value]()
    implicit val ActionTermsType: ObjectType[Unit, ActionTerms] = deriveObjectType[Unit, ActionTerms]()
    implicit val SituationConsequencesType: ObjectType[Unit, SituationConsequences] = deriveObjectType[Unit, SituationConsequences]()
    implicit val ConsequenceType: ObjectType[Unit, Consequence] = deriveObjectType[Unit, Consequence]()
    implicit val SituationType: ObjectType[Unit, Situation] = deriveObjectType[Unit, Situation]()
    implicit val ProgramTermsType: ObjectType[Unit, ProgramTerms] = deriveObjectType[Unit, ProgramTerms]()

    implicit val IdArgType: Argument[Option[Long]] = Argument("id", OptionInputType(LongType))

    val QueryType = ObjectType("Query", fields[ProgramTermsService with SituationService, Unit](
        Field("programTerms", ListType(ProgramTermsType), resolve = _.ctx.getProgramTerms()),
        Field("programTermsById", ProgramTermsType,
              arguments = IdArgType :: Nil,
              resolve = c => c.withArgs(IdArgType)(c.ctx.getProgramTermsById)),
        Field("situations", ListType(SituationType), resolve = _.ctx.getSituations()),
        Field("situationsById", SituationType,
              arguments = IdArgType :: Nil,
              resolve = c => c.withArgs(IdArgType)(c.ctx.getSituationsById))
    ))

    val schema: Schema[ProgramTermsService with SituationService, Unit] = Schema(QueryType)

//    private val source: BufferedSource = Source.fromResource("graphiql.html")

//    println("Printing graphiql.html")
//    source.bufferedReader().lines().forEach(println)
//    println(source)
//source.getLines().mkString("\n")
    HttpObjectsJettyHandler.launchServer(
        80,
        new RootResource,
        new HelloResource1,
        new QueryResource2("", schema, new Services)
    )
}

class HelloResource1 extends HttpObject("/hello/{x}") {
    var count = 0
    override def get(req: Request): Response = {
        val valueForX= req.path.valueFor("x")
        println(s"HelloResource1 get ${valueForX} - request number : $count")
        count += 1

        DSL.OK(DSL.Html(s"<html><body><h1>hello from scala - V6 - ${valueForX}</h1></body></html>"))
    }
}

class RootResource extends HttpObject("/") {
    var count = 0
    override def get(req: Request): Response = {
        val valueForX= req.path.valueFor("x")
        println(s"HelloResource1 get ${valueForX} - request number : $count")
        count += 1

        DSL.OK(DSL.Html(s"<html><body><h1>root hello from scala - V6 - ${valueForX}</h1></body></html>"))
    }
}

case class GQL1(query: String)

class Services extends ProgramTermsService
    with SituationService

class QueryResource2(
    graphiqlHtml: String,
    graphqlSchema: Schema[ProgramTermsService with SituationService, Unit],
    service: Services) extends HttpObject("/query") {
    var count = 0
    override def get(req: Request): Response = {
        println(s"get called - serving ${qraphiqlHtmlString.substring(0,100)}...")
        OK(Html(qraphiqlHtmlString))
    }

    implicit val fooDecoder: Decoder[GQL1] = deriveDecoder


    override def post(req: Request): Response = {
        println(s"GQL post - request number : $count")
        count += 1
        val os = new ByteArrayOutputStream()
        req.representation().write(os)
        val gql = decode[GQL1](new String(os.toByteArray)).right.get
        println(s"GQL = $gql")

        val value = Executor.execute(graphqlSchema, QueryParser.parse(gql.query).get, service)

        DSL.OK(DSL.Json(Await.result(value, 1 second).toString()))
    }

    private val qraphiqlHtmlString = """<!--
                     | * LICENSE AGREEMENT For GraphiQL software
                     | *
                     | * Facebook, Inc. (“Facebook”) owns all right, title and interest, including all
                     | * intellectual property and other proprietary rights, in and to the GraphiQL
                     | * software. Subject to your compliance with these terms, you are hereby granted a
                     | * non-exclusive, worldwide, royalty-free copyright license to (1) use and copy the
                     | * GraphiQL software; and (2) reproduce and distribute the GraphiQL software as
                     | * part of your own software (“Your Software”). Facebook reserves all rights not
                     | * expressly granted to you in this license agreement.
                     | *
                     | * THE SOFTWARE AND DOCUMENTATION, IF ANY, ARE PROVIDED "AS IS" AND ANY EXPRESS OR
                     | * IMPLIED WARRANTIES (INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
                     | * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE) ARE DISCLAIMED. IN NO
                     | * EVENT SHALL FACEBOOK OR ITS AFFILIATES, OFFICES, DIRECTORS OR EMPLOYEES BE
                     | * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
                     | * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
                     | * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
                     | * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
                     | * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
                     | * THE USE OF THE SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
                     | *
                     | * You will include in Your Software (e.g., in the file(s), documentation or other
                     | * materials accompanying your software): (1) the disclaimer set forth above; (2)
                     | * this sentence; and (3) the following copyright notice:
                     | *
                     | * Copyright (c) 2015, Facebook, Inc. All rights reserved.
                     |-->
                     |<!DOCTYPE html>
                     |<html>
                     |<head>
                     |    <style>
                     |      body {
                     |        height: 100%;
                     |        margin: 0;
                     |        width: 100%;
                     |        overflow: hidden;
                     |      }
                     |
                     |      #graphiql {
                     |        height: 100vh;
                     |      }
                     |    </style>
                     |    <title>Hello Kitty CAT Interface API Graphical Shell</title>
                     |    <link rel="stylesheet" href="//cdn.jsdelivr.net/graphiql/0.8.0/graphiql.css" />
                     |    <script src="//cdn.jsdelivr.net/es6-promise/4.0.5/es6-promise.auto.min.js"></script>
                     |    <script src="//cdn.jsdelivr.net/fetch/0.9.0/fetch.min.js"></script>
                     |    <script src="//cdn.jsdelivr.net/react/15.3.2/react.min.js"></script>
                     |    <script src="//cdn.jsdelivr.net/react/15.3.2/react-dom.min.js"></script>
                     |    <script src="//cdn.jsdelivr.net/graphiql/0.8.0/graphiql.min.js"></script>
                     |</head>
                     |<body>
                     |<form>
                     |    <input type="text" id="cjdev-key" placeholder="Enter Dev Key" value="">
                     |</form>
                     |<div id="graphiql">Loading...</div>
                     |
                     |<script>
                     |
                     |      /**
                     |       * This GraphiQL example illustrates how to use some of GraphiQL's props
                     |       * in order to enable reading and updating the URL parameters, making
                     |       * link sharing of queries a little bit easier.
                     |       *
                     |       * This is only one example of this kind of feature, GraphiQL exposes
                     |       * various React params to enable interesting integrations.
                     |       */
                     |
                     |      // Parse the search string to get url parameters.
                     |      var search = window.location.search;
                     |      var parameters = {};
                     |      search.substr(1).split('&').forEach(function (entry) {
                     |        var eq = entry.indexOf('=');
                     |        if (eq >= 0) {
                     |          parameters[decodeURIComponent(entry.slice(0, eq))] =
                     |            decodeURIComponent(entry.slice(eq + 1));
                     |        }
                     |      });
                     |
                     |      // if variables was provided, try to format it.
                     |      if (parameters.variables) {
                     |        try {
                     |          parameters.variables =
                     |            JSON.stringify(JSON.parse(parameters.variables), null, 2);
                     |        } catch (e) {
                     |          // Do nothing, we want to display the invalid JSON as a string, rather
                     |          // than present an error.
                     |        }
                     |      }
                     |
                     |      // When the query and variables string is edited, update the URL bar so
                     |      // that it can be easily shared
                     |      function onEditQuery(newQuery) {
                     |        parameters.query = newQuery;
                     |        updateURL();
                     |      }
                     |
                     |      function onEditVariables(newVariables) {
                     |        parameters.variables = newVariables;
                     |        updateURL();
                     |      }
                     |
                     |      function onEditOperationName(newOperationName) {
                     |        parameters.operationName = newOperationName;
                     |        updateURL();
                     |      }
                     |
                     |      function updateURL() {
                     |        var newSearch = '?' + Object.keys(parameters).filter(function (key) {
                     |          return Boolean(parameters[key]);
                     |        }).map(function (key) {
                     |          return encodeURIComponent(key) + '=' +
                     |            encodeURIComponent(parameters[key]);
                     |        }).join('&');
                     |        history.replaceState(null, null, newSearch);
                     |      }
                     |
                     |      // Defines a GraphQL fetcher using the fetch API.
                     |      function graphQLFetcher(graphQLParams) {
                     |
                     |        return fetch(window.location.origin + '/query', {
                     |          method: 'post',
                     |          headers: {
                     |            'Accept': 'application/json',
                     |            'Content-Type': 'application/json',
                     |            'Authorization': 'Bearer ' + document.getElementById('cjdev-key').value,
                     |          },
                     |          body: JSON.stringify(graphQLParams),
                     |          credentials: 'include',
                     |        }).then(function (response) {
                     |          return response.text();
                     |        }).then(function (responseBody) {
                     |          try {
                     |            return JSON.parse(responseBody);
                     |          } catch (error) {
                     |            return responseBody;
                     |          }
                     |        });
                     |      }
                     |
                     |      // Render <GraphiQL /> into the body.
                     |      ReactDOM.render(
                     |        React.createElement(GraphiQL, {
                     |          fetcher: graphQLFetcher,
                     |          query: parameters.query,
                     |          variables: parameters.variables,
                     |          operationName: parameters.operationName,
                     |          onEditQuery: onEditQuery,
                     |          onEditVariables: onEditVariables,
                     |          onEditOperationName: onEditOperationName
                     |        }),
                     |        document.getElementById('graphiql')
                     |      );
                     |    </script>
                     |</body>
                     |</html>
                     |""".stripMargin
}

