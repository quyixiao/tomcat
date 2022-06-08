package com.luban;

import sun.awt.image.OffScreenImageSource;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.security.KeyStore;

public class TomcatSSLServer {

    private static final String SSL_TYPE = "SSL";

    private static final String KS_TYPE = "JKS";

    private static final String X509 = "SunX509";

    private final static int PORT = 443;
    private static TomcatSSLServer sslServer;
    private SSLServerSocket sslServerSocket;


    public static TomcatSSLServer getInstance() throws Exception {
        if (sslServer == null) {
            sslServer = new TomcatSSLServer();
        }
        return sslServer;
    }

    private TomcatSSLServer() throws Exception {
        SSLContext sslContext = createSSLContext();
        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);
    }

    private SSLContext createSSLContext() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(X509);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(X509);
        String serverKeyStoreFile = "c:\\tomcat.jks";
        String srvPassphrase = "tomcat";
        char[] srvPassword = srvPassphrase.toCharArray();
        KeyStore serverKeyStore = KeyStore.getInstance(KS_TYPE);
        serverKeyStore.load(new FileInputStream(serverKeyStoreFile), srvPassword);
        kmf.init(serverKeyStore, srvPassword);
        String clientKeyStoreFile = "c:\\client.jks";
        String cntPassphrase = "client";
        char[] cntPassword = cntPassphrase.toCharArray();
        KeyStore clientKeyStore = KeyStore.getInstance(KS_TYPE);
        clientKeyStore.load(new FileInputStream(clientKeyStoreFile), cntPassword);
        tmf.init(clientKeyStore);
        SSLContext sslContext = SSLContext.getInstance(SSL_TYPE);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }



    public void startService(){
        SSLSocket cntSocket = null;
        BufferedReader ioReader = null;
        PrintWriter ioWriter = null;
        String tmpMsg = null;
        while (true){
        }
    }
}
