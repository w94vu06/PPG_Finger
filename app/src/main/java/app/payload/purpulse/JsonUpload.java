package app.payload.purpulse;

import android.net.wifi.aware.DiscoverySession;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.SimpleFormatter;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;

public class JsonUpload {
    private Double AF_Similarity, AI_Depression, AI_Heart_age, AI_bshl, AI_bshl_pa, AI_dis, AI_dis_pa,
            AI_medic, BMI, BPc_dia, BPc_sys, BSc, Excellent_no, Lf_Hf, RMSSD, R_height, Shannon_h,
            T_height, Total_Power, ULF, Unacceptable_no, VHF, VLF, dis0bs1_0, dis0bs1_1, dis1bs1_0,
            dis1bs1_1, ecg_Arr, ecg_Arr_percentage, ecg_PVC, ecg_PVC_percentage, ecg_QTc, ecg_Rbv,
            ecg_Tbv, ecg_hr_max, ecg_hr_mean, ecg_hr_min, ecg_rsp, hbp, hr_rsp_rate, meanNN, miny,
            miny_local_total, mood_state, pNN50, sdNN, skin_touch, sym_score_shift066, sys, t_error,
            total_scores, unhrv, way_eat, way_eat_pa, waybp1_0_dia, waybp1_0_sys, waybp1_1_dia,
            waybp1_1_sys, waybs1_0, waybs1_1, year10scores;

    //    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    private static final OkHttpClient client = new OkHttpClient();
    private static String json;
    Handler mHandler = new Handler();

    public void jsonUploadToServer(long[] time_dist) {

        JSONArray jsonTimeDist = new JSONArray();
        for (int i = 0; i < time_dist.length; i++) {
            jsonTimeDist.put(time_dist[i]);
        }
        JSONObject jsonData = new JSONObject();
        String date = new SimpleDateFormat("yyyyMMddHHmmss",
                Locale.getDefault()).format(System.currentTimeMillis());

        try {
            jsonData.put("filename", date);
            jsonData.put("id_num", "888889");
            jsonData.put("chaurl", "-1");
            //proFile
            jsonData.put("old", "-1");
            jsonData.put("sex", "-1");
            jsonData.put("height", "-1");
            jsonData.put("weight", "-1");
            jsonData.put("sys", "-1");
            jsonData.put("diabetes", "-1");
            jsonData.put("smokes", "-1");
            jsonData.put("hbp", "-1");
            jsonData.put("morningdiabetes", "-1");
            jsonData.put("aftermealdiabetes", "-1");
            jsonData.put("userstatus", "-1");
            jsonData.put("mealstatus", "-1");
            jsonData.put("medicationstatus", "-1");
            jsonData.put("hbpSBp", "-1");
            jsonData.put("hbpDBp", "-1");
            jsonData.put("md_num", "-1");

            jsonData.put("rri", jsonTimeDist);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jsonString = jsonData.toString();

//        jsonString = jsonString.replace("[", "").replace("]", "");


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

        new Thread() {
            @Override
            public void run() {
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(mediaType, jsonString);
                Request request = new Request.Builder()
                        .url("http://192.168.2.11:8090")
//                        .url("http://192.168.2.110:5000")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);
                    Message msg = Message.obtain();
                    String res = Objects.requireNonNull(response.body()).string();
                    msg.obj = res;
                    Log.d("sqq", "run: " + res);
                    mHandler = new MHandler();
                    mHandler.sendMessage(msg);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

   class MHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            json = msg.obj.toString();
            unpackJson();
        }
    }

    private void unpackJson() {
        new Thread(()->{
            try {
                JSONObject jsonObject = new JSONObject(json);
                AF_Similarity = jsonObject.getDouble("AF_Similarity");
                AI_Depression = jsonObject.getDouble("AI_Depression");
                AI_Heart_age = jsonObject.getDouble("AI_Heart_age");
                AI_bshl = jsonObject.getDouble("AI_bshl");
                AI_bshl_pa = jsonObject.getDouble("AI_bshl_pa");
                AI_dis = jsonObject.getDouble("AI_dis");
                AI_dis_pa = jsonObject.getDouble("AI_dis_pa");
                AI_medic = jsonObject.getDouble("AI_medic");
                BMI = jsonObject.getDouble("BMI");
                BPc_dia = jsonObject.getDouble("BPc_dia");
                BPc_sys = jsonObject.getDouble("BPc_sys");
                BSc = jsonObject.getDouble("BSc");
                Excellent_no = jsonObject.getDouble("Excellent_no");
                Lf_Hf = jsonObject.getDouble("Lf_Hf");
                RMSSD = jsonObject.getDouble("RMSSD");
                R_height = jsonObject.getDouble("R_height");
                Shannon_h = jsonObject.getDouble("Shannon_h");
                T_height = jsonObject.getDouble("T_height");
                Total_Power = jsonObject.getDouble("Total_Power");
                ULF = jsonObject.getDouble("ULF");
                Unacceptable_no = jsonObject.getDouble("Unacceptable_no");
                VHF = jsonObject.getDouble("VHF");
                VLF = jsonObject.getDouble("VLF");
                dis0bs1_0 = jsonObject.getDouble("dis0bs1_0");
                dis0bs1_1 = jsonObject.getDouble("dis0bs1_1");
                dis1bs1_0 = jsonObject.getDouble("dis1bs1_0");
                dis1bs1_1 = jsonObject.getDouble("dis1bs1_1");
                ecg_Arr = jsonObject.getDouble("ecg_Arr");
                ecg_Arr_percentage = jsonObject.getDouble("ecg_Arr_percentage");
                ecg_PVC = jsonObject.getDouble("ecg_PVC");
                ecg_PVC_percentage = jsonObject.getDouble("ecg_PVC_percentage");
                ecg_QTc = jsonObject.getDouble("ecg_QTc");
                ecg_Rbv = jsonObject.getDouble("ecg_Rbv");
                ecg_Tbv = jsonObject.getDouble("ecg_Tbv");
                ecg_hr_max = jsonObject.getDouble("ecg_hr_max");
                ecg_hr_mean = jsonObject.getDouble("ecg_hr_mean");
                ecg_hr_min = jsonObject.getDouble("ecg_hr_min");
                ecg_rsp = jsonObject.getDouble("ecg_rsp");
                hbp = jsonObject.getDouble("hbp");
                hr_rsp_rate = jsonObject.getDouble("hr_rsp_rate");
                meanNN = jsonObject.getDouble("meanNN");
                miny = jsonObject.getDouble("miny");
                miny_local_total = jsonObject.getDouble("miny_local_total");
                mood_state = jsonObject.getDouble("mood_state");
                pNN50 = jsonObject.getDouble("pNN50");
                sdNN = jsonObject.getDouble("sdNN");
                skin_touch = jsonObject.getDouble("skin_touch");
                sym_score_shift066 = jsonObject.getDouble("sym_score_shift066");
                sys = jsonObject.getDouble("sys");
                t_error = jsonObject.getDouble("t_error");
                total_scores = jsonObject.getDouble("total_scores");
                unhrv = jsonObject.getDouble("unhrv");
                way_eat = jsonObject.getDouble("way_eat");
                way_eat_pa = jsonObject.getDouble("way_eat_pa");
                waybp1_0_dia = jsonObject.getDouble("waybp1_0_dia");
                waybp1_0_sys = jsonObject.getDouble("waybp1_0_sys");
                waybp1_1_dia = jsonObject.getDouble("waybp1_1_dia");
                waybp1_1_sys = jsonObject.getDouble("waybp1_1_sys");
                waybs1_0 = jsonObject.getDouble("waybs1_0");
                waybs1_1 = jsonObject.getDouble("waybs1_1");
                year10scores = jsonObject.getDouble("year10scores");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();
    }


}
