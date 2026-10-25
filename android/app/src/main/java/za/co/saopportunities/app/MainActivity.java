package za.co.saopportunities.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SA Opportunities: reads data/jobs.json published by GitHub Actions.
 *  Shows the verification badge + checks, and opens ONLY the stored apply_url
 *  (for verified jobs that is the official employer's direct link). */
public class MainActivity extends Activity {
    private final List<JSONObject> all = new ArrayList<>(), shown = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView status;
    private boolean verifiedOnly = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        findViewById(R.id.btnRefresh).setOnClickListener(v -> load());
        findViewById(R.id.btnAll).setOnClickListener(v -> { verifiedOnly = false; render(); });
        findViewById(R.id.btnVerified).setOnClickListener(v -> { verifiedOnly = true; render(); });
        list.setOnItemClickListener((p, v, pos, id) -> {
            JSONObject j = shown.get(pos);
            new android.app.AlertDialog.Builder(this)
                .setTitle(j.optString("title"))
                .setMessage(details(j))
                .setNegativeButton("Close", null)
                .setPositiveButton("Open official apply link", (d, w) ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(j.optString("apply_url")))))
                .show();
        });
        load();
    }

    private String details(JSONObject j) {
        JSONObject v = j.optJSONObject("verification");
        StringBuilder s = new StringBuilder();
        s.append(j.optString("company")).append("\n\n");
        s.append(v != null && "verified_official".equals(v.optString("status"))
            ? "✅ VERIFIED on the employer's official site" : "⚠️ Board listing — not yet traced to the employer").append("\n");
        if (v != null) {
            s.append("Confidence: ").append(v.optInt("confidence")).append("%\n\nChecks done:\n");
            JSONArray c = v.optJSONArray("checks");
            if (c != null) for (int i = 0; i < c.length(); i++) s.append("• ").append(c.optString(i)).append("\n");
            JSONArray w = v.optJSONArray("warnings");
            if (w != null && w.length() > 0) { s.append("\nWarnings:\n"); for (int i = 0; i < w.length(); i++) s.append("• ").append(w.optString(i)).append("\n"); }
        }
        s.append("\nNever pay anyone to apply for a job.");
        return s.toString();
    }

    private void render() {
        shown.clear(); adapter.clear();
        for (JSONObject j : all) {
            JSONObject v = j.optJSONObject("verification");
            boolean ver = v != null && "verified_official".equals(v.optString("status"));
            if (verifiedOnly && !ver) continue;
            shown.add(j);
            adapter.add((ver ? "✅ " : "⚠️ ") + j.optString("title") + "\n" + j.optString("company"));
        }
        adapter.notifyDataSetChanged();
        status.setText(shown.size() + " listing(s)" + (verifiedOnly ? " — verified only" : ""));
    }

    private void load() {
        status.setText("Loading verified SA jobs…");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.JOBS_JSON_URL).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONArray arr = new JSONObject(sb.toString()).optJSONArray("jobs");
                all.clear();
                if (arr != null) for (int i = 0; i < arr.length(); i++) all.add(arr.getJSONObject(i));
                runOnUiThread(this::render);
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Could not load jobs. Check your connection and tap Refresh."));
            }
        }).start();
    }
}
