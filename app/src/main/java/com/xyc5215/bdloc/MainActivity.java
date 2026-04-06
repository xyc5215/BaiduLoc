package com.xyc5215.bdloc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private TextView tvResult;
    private Button btnRefresh;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        tvResult = (TextView) findViewById(R.id.tv_result);
        btnRefresh = (Button) findViewById(R.id.btn_refresh);
        
        // 首次加载自动定位
        startLocation();
        
        // 刷新按钮点击事件
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocation();
            }
        });
    }

    private void startLocation() {
        tvResult.setText("正在定位中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String rawJson = requestBaiduLoc();
                final String parsedResult = parseLocationJson(rawJson);
                
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResult.setText(parsedResult);
                    }
                });
            }
        }).start();
    }

    // 百度定位请求（完全匹配curl）
    private String requestBaiduLoc() {
        try {
            URL url = new URL("https://loc.map.baidu.com/sdk.php");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            
            // 请求头
            conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 8.0.0; AUM-AL20 Build/HONORAUM-AL20)");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            conn.setRequestProperty("Cookie", "BAIDUID=87B12A0116183D0994345F71B155B047:FG=1; BAIDUID_BFESS=87B12A0116183D0994345F71B155B047:FG=1");
            
            // 参数（trtm动态生成时间戳）
            Map<String, String> params = new HashMap<>();
            params.put("bloc", "00PGysyfyc3AxIME3f5F8p7dWQ4SIFrzL4P-GkXTNMtXYpzqO8n7z2jE9SlwN4lZ0_I3ziGOYAfe9FilTPyPi8EVugBsivtp8_hkj-mT2EYzcD6hlzFA7tyT-O6MUVo-2ucL6NQMcSBjR3hLniugreCI0Gb1pzYfrm9X505HVyxmfOMnIBRoqoLw5g0UDyUjMZIG7bDSBK4z3AyV9Znq8JGJ0GLNmxznTG6YPm_PVc3Ctdd3qWUu7rYidHdHsQM8l_..|tp=4");
            params.put("trtm", String.valueOf(System.currentTimeMillis()));
            
            // 拼接参数
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, String> p : params.entrySet()) {
                if (postData.length() > 0) postData.append("&");
                postData.append(URLEncoder.encode(p.getKey(), "UTF-8"));
                postData.append("=");
                postData.append(URLEncoder.encode(p.getValue(), "UTF-8"));
            }
            
            // 发送请求
            byte[] bytes = postData.toString().getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            
            // 读取返回
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = conn.getInputStream().read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            String json = bos.toString("UTF-8");
            
            conn.disconnect();
            bos.close();
            
            return json;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.toString() + "\"}";
        }
    }

    // JSON解析成可读地址
    private String parseLocationJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject result = root.optJSONObject("result");
            String errorCode = result != null ? result.optString("error", "未知") : "未知";
            String time = result != null ? result.optString("time", "未知时间") : "未知时间";

            // 解析错误码
            String errorTip = "";
            if (!"161".equals(errorCode)) {
                errorTip = "⚠️ 定位异常，错误码：" + errorCode + "\n\n";
            }

            JSONObject content = root.optJSONObject("content");
            if (content == null) {
                return errorTip + "定位失败：无定位数据\n原始JSON：\n" + json;
            }

            JSONObject addr = content.optJSONObject("addr");
            JSONObject point = content.optJSONObject("point");
            String radius = content.optString("radius", "未知");

            // 拼接地址
            StringBuilder address = new StringBuilder();
            if (addr != null) {
                address.append(addr.optString("country", "")).append(" ");
                address.append(addr.optString("province", "")).append(" ");
                address.append(addr.optString("city", "")).append(" ");
                address.append(addr.optString("district", "")).append(" ");
                address.append(addr.optString("street", "")).append("");
                address.append(addr.optString("street_number", "")).append("\n");
                address.append("📍 详细地址：").append(addr.optString("province", "")).append(addr.optString("city", "")).append(addr.optString("district", "")).append(addr.optString("street", "")).append(addr.optString("street_number", "")).append("\n\n");
            }

            // 拼接经纬度
            if (point != null) {
                String lng = point.optString("x", "未知");
                String lat = point.optString("y", "未知");
                address.append("🌐 经纬度：").append(lng).append(" , ").append(lat).append("\n");
                address.append("📏 定位精度：约").append(radius).append("米\n");
            }

            address.append("\n⏰ 定位时间：").append(time);
            address.append("\n\n--- 原始JSON ---\n").append(json);

            return errorTip + address.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "解析失败\n原始JSON：\n" + json;
        }
    }
}
