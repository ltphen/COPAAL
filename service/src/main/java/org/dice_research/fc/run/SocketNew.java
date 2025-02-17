package org.dice_research.fc.run;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.dice_research.fc.IFactChecker;
import org.dice_research.fc.config.RequestParameters;
import org.dice_research.fc.data.FactCheckingResult;
import org.dice_research.fc.paths.verbalizer.IPathVerbalizer;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Component()
public class SocketNew  {

    /*
        This class contains code for Socket Communication between client and server.
        Once the Client connects to the socket, the server will be listening indefinitely for assertions.
        Once the truth value is computed, it will send the result value back to the user.
        Socket is running on the port 3333.
        For testing purpose, after running the copaal application using mvn clean install,
        You can use the command "nc localhost 3333" to connect to the server.
        And use this triple as an input : {"type": "test", "subject": "http://dbpedia.org/resource/Barack_Obama",
        "object": "http://dbpedia.org/resource/United_States",  "predicate": "http://dbpedia.org/ontology/nationality"}

    */

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketNew.class);

    @Autowired
    ApplicationContext ctx;

    //InputStream to read the contents from the client
    InputStream inputStream = null;

    //OutputStream to send the data to the client
    DataOutputStream outputStream  = null;

    //
    BufferedReader bufferedReader = null;

    //ServerSocket Object
    ServerSocket serverSocket = null;

    //Client Socket Object
    Socket clientSocket = null;

    public void serverStart(int portNumber) {
        /*
        *  Code for staring the server socket in the given port number.
        *  The server is being start on different thread.
        *
        * */
//        checkWhetherFusekiServerIsRunning();
        Runnable serverTask = () -> {
            try {
                serverSocket = new ServerSocket(portNumber);
                LOGGER.info("Socket is up and running on Local Port" + serverSocket.getLocalPort() + " and on Local Socket Address"+serverSocket.getLocalSocketAddress() );
                LOGGER.info("Waiting for clients to connect...");
                while (true) {
                    clientSocket = serverSocket.accept();
                    LOGGER.info("Client Accepted " + clientSocket.getLocalPort() + "  " + clientSocket.getPort());
                    listenAndRespondToData();
                }
            } catch (IOException e) {
                System.err.println("Unable to process client request");
                e.printStackTrace();
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
        serverThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LOGGER.info("SOME EXCEPTION OCCURED " + e);
                sendResult(-1.0,e.toString());
            }
        });
    }

    private void checkWhetherFusekiServerIsRunning() throws IOException {
        /*
            We have our own dbpedia endpoint for validating facts and the data is hosted in
            Apache Jena Fuseki Server. In order for our application to work fine, we need the fuseki server
            to be up and running. This code is to check whether the fuseki server is up and running .
        */
        HttpClient client;
        HttpRequestBase request = null;
        String service = "http://127.0.0.1:3030/ds/sparql";
        HttpPost post = new HttpPost(service);
        String query = "SELECT * WHERE {\n" +
                "  ?sub ?pred ?obj .\n" +
                "} LIMIT 2";
        ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("query", query.toString()));
        post.setEntity(new UrlEncodedFormEntity(postParameters,"UTF-8"));
        request = post;
        request.addHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
        request.addHeader(HttpHeaders.ACCEPT, "application/sparql-results+xml");
        client = HttpClients
                .custom()
                .build();
        HttpResponse httpResponse = client.execute(request);
        String result = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
        ResultSet resultSet = ResultSetFactory.fromXML(result);
        if(resultSet.hasNext()){
            if(!resultSet.nextBinding().isEmpty()){
                LOGGER.info("Apache Jena Fuseki Server is Up and Running");
            }
        }
    }

    public void listenAndRespondToData(){
        /*
            Here is where the user input is being listened by the server using Socket InputStream.
            We get the data in jsonFormat, need to unwrap data from th json.
         */
        try {
            inputStream = clientSocket.getInputStream();
            outputStream = new DataOutputStream(clientSocket.getOutputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            while (true) {
                String data;
                DataInputStream in = new DataInputStream(inputStream);
                byte[] buffer = new byte[1024]; // or 4096, or more
                in.read(buffer);
                data = new String(buffer, StandardCharsets.UTF_8).trim();
                // At the eof, empty line is being sent to avoid that adding this check
                if(data.equals("")){
//                    LOGGER.info("Connection closed");
//                    clientSocket.close();
                    return;
                }
                if(data.charAt(0) == 'b') {
                    data = data.substring(2, data.length() - 1);
                }
                JSONObject jsonObject = new JSONObject(data);
                if(jsonObject.getString("type").equals("call") && (jsonObject.getString("content").equals("type"))){
                    JSONObject acknowledgeresponse = new JSONObject();
                    acknowledgeresponse.put("type","type_response");
                    acknowledgeresponse.put("content","unsupervised");
                    outputStream.write(acknowledgeresponse.toString().getBytes(StandardCharsets.UTF_8));
                }
                else{
                    LOGGER.info("GOT DATA AND DATA IS " + jsonObject);
                    String subject = jsonObject.getString("subject");
                    String property = jsonObject.getString("predicate");
                    String object = jsonObject.getString("object");
                    LOGGER.info("GOT DATA AND Subject is  " + subject + " and object is " + object + "and the predicate is" + property);
                    try {
                        FactCheckingResult result = evaluateTriples(subject,object,property);
                        sendResult(result.getVeracityValue(),"");
                    } catch (Exception e) {
                        LOGGER.info("SOME EXCEPTION OCCURED " + e);
                        sendResult(-1.0, e.toString());
                    }
                }
            }
        }
        catch(IOException ioException){
            ioException.printStackTrace();
        }
    }

    public FactCheckingResult evaluateTriples(String subject, String object, String property){
        /*
            The main method of evaluating the triples given by the user.
         */

        Resource subjectURI = ResourceFactory.createResource(subject);
        Resource objectURI = ResourceFactory.createResource(object);
        Property propertyURI = ResourceFactory.createProperty(property);
        LOGGER.info("The data are " + subjectURI + " " + objectURI + " " + propertyURI);
        IFactChecker factChecker = ctx.getBean(IFactChecker.class);
        FactCheckingResult result = factChecker.check(subjectURI, propertyURI, objectURI);

        IPathVerbalizer verbalizer = ctx.getBean(IPathVerbalizer.class, ctx.getBean(QueryExecutionFactory.class), new RequestParameters());
        verbalizer.verbalizeResult(result);

        LOGGER.info("Result is " + result.getVeracityValue());
        return result;
    }

    public void sendResult(double result, String exception){
        /*
          The method to send the result back to the user via the socketOutputStream.
         */
        JSONObject response = new JSONObject();
        response.put("type","test_result");
        response.put("score",String.valueOf(result));
        response.put("exception",exception);
        try {
            outputStream.write(response.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void startSocketProcedure() {
        serverStart(3333);

//        listenAndRespondToData();
    }
}
