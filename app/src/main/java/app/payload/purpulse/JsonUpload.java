package app.payload.purpulse;

import android.net.wifi.aware.DiscoverySession;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;

public class JsonUpload {

//    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    private static final OkHttpClient client = new OkHttpClient();
    Handler mHandler = new Handler();
    public void jsonUploadToServer(long[] time_dist) {

        JSONArray jsonTimeDist = new JSONArray();
        for (int i = 0; i < time_dist.length; i++) {
            jsonTimeDist.put(time_dist[i]);
        }
        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("RMSSD","A");
            jsonData.put("SDNN", "A");
            jsonData.put("filename", "A");
            jsonData.put("id_num", "A");
            jsonData.put("chaurl", "A");
            jsonData.put("old", "A");
            jsonData.put("sex", "A");
            jsonData.put("height", "A");
            jsonData.put("weight", "A");
            jsonData.put("sys", "A");
            jsonData.put("diabetes", "A");
            jsonData.put("smokes", "A");
            jsonData.put("hop", "A");
            jsonData.put("morningdiabetes", "A");
            jsonData.put("aftermealdiabetes", "A");
            jsonData.put("userstatus", "A");
            jsonData.put("mealstatus", "A");
            jsonData.put("medicationstatus", "A");
            jsonData.put("hbpSBp", "A");
            jsonData.put("hbpDBp", "A");
            jsonData.put("md_num", "A");
            jsonData.put("rri", jsonTimeDist);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jsonString = jsonData.toString();

//        jsonString = jsonString.replace("[", "").replace("]", "");
        Log.d("tttt", "jsonUploadToServer: " + jsonString);


//        OkHttpClient.Builder builder = new OkHttpClient.Builder()
//                .connectionSpecs(Collections.singletonList(ConnectionSpec.COMPATIBLE_TLS));
//        List<ConnectionSpec> connectionSpecs = new ArrayList<>();
//        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
//                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
//                .allEnabledCipherSuites()
//                .build();
//        connectionSpecs.add(spec);
//
//        ConnectionSpec spec1 = new ConnectionSpec.Builder(ConnectionSpec.CLEARTEXT).build();
//        connectionSpecs.add(spec1);
//        builder.connectionSpecs(connectionSpecs);
//
//        ConnectionSpec spec2 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
//                .tlsVersions(TlsVersion.TLS_1_2)
//                .build();
//        connectionSpecs.add(spec2);
//
//        OkHttpClient client = builder.build();
        String finalJsonString = jsonString;
        new Thread() {
            @Override
            public void run() {
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(mediaType, finalJsonString);
                Request request = new Request.Builder()
//                        .url("http://192.168.2.11:8090")
                        .url("http://192.168.2.110:5000")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);
                    Log.d("sqq", "run: " + Objects.requireNonNull(response.body()).string());
                    Message msg = Message.obtain();
                    msg.what = 1;
                    mHandler.sendMessage(msg);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

}
