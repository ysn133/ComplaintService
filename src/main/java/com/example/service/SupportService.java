package com.example.service;


import com.example.model.support;
import java.util.*;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author HP
 */
public class SupportService {
    private static final String BASE_URL = "http://localhost:8091/api/supports";

    public List<support> getAllSupports(String token) {
        List<support> supportList = new ArrayList<>();
        try {
            URL url = new URL(BASE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            if (conn.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                supportList = new Gson().fromJson(reader, new TypeToken<List<Support>>() {}.getType());
                reader.close();
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return supportList;
    }

}
