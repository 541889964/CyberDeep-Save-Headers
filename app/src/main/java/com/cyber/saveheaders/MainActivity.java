package com.cyber.saveheaders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebMessage;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private GeckoView geckoView;
    private GeckoSession session;
    private static GeckoRuntime runtime;
    private TextView tvStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 申请存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        geckoView = findViewById(R.id.geckoView);
        tvStatus = findViewById(R.id.tvStatus);

        if (runtime == null) runtime = GeckoRuntime.create(this);
        session = new GeckoSession();

        // 接收网页 JS 偷来的 Header
        session.setMessageDelegate(new GeckoSession.MessageDelegate() {
            @Override
            public void onMessage(@NonNull GeckoSession s, String uri, @NonNull WebMessage message, boolean isUserGesture, Long requestId) {
                String data = message.data;
                if (data.startsWith("STOLEN_HEADERS:")) {
                    String headersJson = data.substring(15);
                    mainHandler.post(() -> saveHeadersToDownload(headersJson));
                }
            }
        }, "https://chat.deepseek.com");

        session.open(runtime);
        geckoView.setSession(session);
        session.loadUri("https://chat.deepseek.com");

        // 延迟注入偷包脚本
        mainHandler.postDelayed(this::injectStealScript, 3000);
    }

    private void injectStealScript() {
        String js = 
            "(function() {" +
            "  if (window.cyberHooked) return;" +
            "  window.cyberHooked = true;" +
            "  let origFetch = window.fetch;" +
            "  window.fetch = async function() {" +
            "    let url = arguments[0];" +
            "    let options = arguments[1];" +
            "    if (typeof url === 'string' && url.includes('/chat/completion')) {" +
            "      if (options && options.headers) {" +
            "        let headersObj = {};" +
            "        if (typeof options.headers.forEach === 'function') {" +
            "           options.headers.forEach((v, k) => { headersObj[k] = v; });" +
            "        } else { headersObj = options.headers; }" +
            "        let headersStr = JSON.stringify(headersObj);" +
            "        window.postMessage('STOLEN_HEADERS:' + headersStr, '*');" +
            "      }" +
            "    }" +
            "    return origFetch.apply(this, arguments);" +
            "  };" +
            "})();";
        session.loadUri("javascript:" + js);
        tvStatus.setText("抓包引擎已就绪");
    }

    private void saveHeadersToDownload(String headersJson) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(downloadDir, "DeepSeek_Headers_" + timeStamp + ".json");
            
            FileWriter writer = new FileWriter(file);
            writer.write(headersJson);
            writer.close();

            tvStatus.setText("✅ 提取成功！已保存至Download");
            Toast.makeText(this, "签名已保存：" + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            tvStatus.setText("❌ 保存失败: " + e.getMessage());
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
