package com.mycompany.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.net.HttpURLConnection;
import java.io.IOException;

@Configuration
public class WebSocketClientConfig {

    private static final Logger logger = Logger.getLogger(WebSocketClientConfig.class.getName());

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void init() {
        logger.info("WebSocketClientConfig loaded - Version 2025-05-14-1711");
    }

    @PostConstruct
    public void connectToTicketService() {
        try {
            logger.info("Attempting to connect to ticket service WebSocket at wss://192.168.0.102:8093/ws");

            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            sslContext.init(null, trustAllCerts, new SecureRandom());

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5000);
            requestFactory.setReadTimeout(5000);
            requestFactory.setBufferRequestBody(false);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                        ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
                        ((javax.net.ssl.HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            });

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
            RestTemplateXhrTransport xhrTransport = new RestTemplateXhrTransport(restTemplate);
            SockJsClient sockJsClient = new SockJsClient(Arrays.asList(xhrTransport, new WebSocketTransport(webSocketClient)));
            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

            MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
            stompClient.setMessageConverter(converter);

            String url = "wss://192.168.0.102:8093/ws";
            stompClient.connect(url, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    logger.info("Successfully connected to ticket service WebSocket: " + url);
                    logger.info("Connected headers: " + connectedHeaders);

                    session.subscribe("/topic/tickets/created", new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            logger.info("Received message headers: " + headers);
                            return Map.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            logger.info("Received ticket creation message: " + payload);
                            try {
                                messagingTemplate.convertAndSend("/app/tickets/created", payload);
                                logger.info("Forwarded ticket creation message to /app/tickets/created");
                                messagingTemplate.convertAndSend("/topic/tickets/created", payload);
                                logger.info("Broadcasted ticket creation message to /topic/tickets/created as fallback");
                            } catch (Exception e) {
                                logger.severe("Failed to process ticket creation message: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
                    logger.info("Subscribed to /topic/tickets/created");
                }

                @Override
                public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                            byte[] payload, Throwable exception) {
                    logger.severe("WebSocket error: " + exception.getMessage() + ", Command: " + command + ", Headers: " + headers);
                    exception.printStackTrace();
                }

                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    logger.severe("WebSocket transport error: " + exception.getMessage());
                    exception.printStackTrace();
                }
            });
        } catch (Exception e) {
            logger.severe("Failed to connect to ticket service WebSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }
}