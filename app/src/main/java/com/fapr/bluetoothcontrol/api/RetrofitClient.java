package com.fapr.bluetoothcontrol.api;

import androidx.annotation.NonNull;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;

/**
 * Manage the retrofit configuration
 */
public class RetrofitClient {
    private Retrofit retrofit;
    private final String baseUri;
    private static final boolean IGNORE_SSL = false;

    public RetrofitClient(String baseUri) {
        this.baseUri = baseUri;
    }

    public Retrofit getRetrofitClient() {
        if (retrofit == null) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

            if (IGNORE_SSL) {
                clientBuilder = getUnsafeOkHttpClient(clientBuilder);
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUri)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(clientBuilder.build())
                    .build();
        }
        return retrofit;
    }

    @NonNull
    private OkHttpClient.Builder getUnsafeOkHttpClient(OkHttpClient.Builder builder) {
        try {
            // Create a trust manager
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create SSL socket
            final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
