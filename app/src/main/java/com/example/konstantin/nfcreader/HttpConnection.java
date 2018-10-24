package com.example.konstantin.nfcreader;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpConnection extends AsyncTask<String, Void, String> {
    private OkHttpClient client = new OkHttpClient();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private Context context;



    public HttpConnection(Context context) {
        this.context = context;
    }

    String getResponse(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    String bowlingJson(String player1) {
        return "";
}

    @Override
    protected String doInBackground(String... strings) {
        if (strings.length == 2) {
            try {
                return post(strings[0], strings[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (strings.length == 1) {
            try {
                String str = getResponse(strings[0]);
                JSONObject obj = new JSONObject(str);
                String ip = obj.getString("origin");
                return ip;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(String result) {
        ((MainActivity)context).changeText(result);
    }
}

