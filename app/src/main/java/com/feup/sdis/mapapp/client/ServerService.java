package com.feup.sdis.mapapp.client;

import android.app.Activity;
import android.app.Application;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.feup.sdis.mapapp.MainActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * This class implements the required HTTP communication methods
 */
public class ServerService extends AsyncTask<String, Void, String>
                        implements ClientInterface{

    /** URL to reach **/
    protected URL url;
    /** HttpURLConnection to make the communication **/
    protected HttpsURLConnection urlConnection;
    /** The response returned by the server **/
    String response;
    /** HTTP response code **/
    int responseCode;
    /** Server CA **/
    InputStream certStream;
    /** Trust Certificate **/
    InputStream trustStream;
    /** boa passe **/
    char[] password = "123456".toCharArray();

    Activity activity;

    /** Default Constructor, does nothing **/
    public ServerService(Activity activity, InputStream certStream, InputStream trustStream) {
        this.url = null;
        this.urlConnection = null;
        this.response = null;
        this.certStream = certStream;
        this.trustStream = trustStream;
        this.activity =activity;

        if (certStream == null || trustStream == null) {
            Log.i("Cert", "Null Certs!");
        }
    }

    public ServerService(InputStream certStream, InputStream trustStream) {
        this.url = null;
        this.urlConnection = null;
        this.response = null;
        this.certStream = certStream;
        this.trustStream = trustStream;
        this.activity = null;

        if (certStream == null || trustStream == null) {
            Log.i("Cert", "Null Certs!");
        }
    }

    public void onResponseReceived(String s) {
    }

    @Override
    public String doInBackground(String... parameters) {

        /** Parameters:
         *  0 - context
         *  1 - Method
         *  2 - Post body (PUT,POST)
         * **/

        try {
            // "http://10.0.2.2:8000/maps?name=mapa1
            url = new URL("https://172.30.2.216:8000/" + parameters[0]);
            String method = parameters[1];

            if (certStream == null || trustStream == null){
                return null;
            }

            OurHostnameVerifier verifier = new OurHostnameVerifier();

            SSLSocketFactory socketF = initSSL();
            urlConnection.setDefaultSSLSocketFactory(socketF);
            urlConnection.setDefaultHostnameVerifier(verifier);

            urlConnection = (HttpsURLConnection) url.openConnection();

            urlConnection.setRequestMethod(method);

            switch(method) {
                case "GET":{
                    getResponse(urlConnection);
                    break;
                }
                case "POST": {
                    String body = parameters[2];
                    postBody(urlConnection, body);
                    break;
                }
                case "DELETE": {
                    getResponse(urlConnection);
                    break;
                }
                case "PUT" : {
                    String body = parameters[2];
                    postBody(urlConnection, body);
                    break;
                }
                default: return null;
            }
            return String.valueOf(this.responseCode) + " - " + this.response;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(String s){
        onResponseReceived(s);
    }

    private void getResponse(HttpURLConnection urlConnection) {

        try {
            this.responseCode = urlConnection.getResponseCode();
            Log.i("code", Integer.valueOf(responseCode).toString());
        } catch (IOException e){
            e.printStackTrace();
            return;
        }

        try {
            InputStreamReader isw = new InputStreamReader(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(isw);

            String response = "";
            String line = "";

            while ((line = reader.readLine()) != null) {
                response += line;
            }

            this.response = response;
        }
        catch (Exception e ) {
            Log.i("cas", Integer.valueOf(responseCode).toString() + " .. " + this.response);
            this.response = null;
        }
        Log.i("Here",this.responseCode + " - " + this.response);
    }

    private void postBody(HttpURLConnection urlConnection, String requestBody) throws IOException {

        urlConnection.setDoOutput(true);
        urlConnection.setChunkedStreamingMode(0);

        DataOutputStream out = new DataOutputStream(urlConnection.getOutputStream());
        out.write(requestBody.getBytes());

        Log.i("LOGGGG", requestBody);

        getResponse(urlConnection);

    }

    public static int decodeResponse(String fullResponse) {
        return Integer.parseInt(fullResponse.split(" - ")[0]);
    }

    private SSLSocketFactory initSSL() throws
            KeyStoreException,
            NoSuchAlgorithmException,
            CertificateException,
            IOException,
            UnrecoverableKeyException,
            KeyManagementException
    {

        // Load KeyStore With Bouncy Castle protocol BKS
        KeyStore keystore = KeyStore.getInstance("BKS");
        keystore.load(MyApp.getContext().getAssets().open("testks.bks"), password);

        // Load TrustStore because server uses self-signed certificate
        KeyStore trustStore = KeyStore.getInstance("BKS");
        trustStore.load(MyApp.getContext().getAssets().open("truststore.bks"), password);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keystore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(trustStore);

        // Load SSL Context
        SSLContext sslctx = SSLContext.getInstance("TLS");
        sslctx.init(
                kmf.getKeyManagers(),
                tmf.getTrustManagers(),
                new SecureRandom()
        );

        SSLSocketFactory socketF = sslctx.getSocketFactory();
        return socketF;
    }

}
