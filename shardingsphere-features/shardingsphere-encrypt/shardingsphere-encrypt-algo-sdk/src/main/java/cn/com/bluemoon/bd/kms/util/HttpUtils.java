package cn.com.bluemoon.bd.kms.util;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * @author Jarod.Kong
 */
public class HttpUtils {

    /**
     * http sync get
     */
    public static String doGet(String url) throws IOException {
        OkHttpClient okHttpClient = new OkHttpClient();
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = okHttpClient.newCall(request);
        try (Response res = call.execute()) {
            return res.body().string();
        }
    }
}
