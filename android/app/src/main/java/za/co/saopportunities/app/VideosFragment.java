package za.co.saopportunities.app;
import android.app.AlertDialog; import android.content.Intent; import android.net.Uri;
import android.os.Bundle; import android.view.*; import android.widget.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment;
import org.json.*; import java.util.*;
public class VideosFragment extends Fragment {
    private LinearLayout list; private TextView status;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        View v = in.inflate(R.layout.fragment_videos, c, false);
        list = v.findViewById(R.id.videoList); status = v.findViewById(R.id.videoStatus);
        Net.get(BuildConfig.OPP_JSON_URL, new Net.Cb(){
            public void ok(String body){ run(() -> show(body)); }
            public void fail(Exception e){ run(() -> status.setText("Could not load. Check your connection and reopen this tab.")); }
        });
        return v;
    }
    private void run(Runnable r){ if (getActivity() != null) getActivity().runOnUiThread(r); }
    private void show(String body){
        try {
            JSONArray a = new JSONArray(body); status.setText("Silent step-by-step guides — no voice, just follow the steps. New videos are made daily and approved before posting.");
            for (int i = 0; i < a.length(); i++){
                JSONObject o = a.getJSONObject(i);
                View card = LayoutInflater.from(getContext()).inflate(R.layout.item_job, list, false);
                ((TextView) card.findViewById(R.id.jobTitle)).setText("▶ " + o.optString("title"));
                ((TextView) card.findViewById(R.id.jobCompany)).setText(o.optString("category"));
                ((TextView) card.findViewById(R.id.jobMeta)).setText(o.optString("sa_note"));
                TextView badge = card.findViewById(R.id.jobBadge); badge.setText("📺 SILENT VIDEO GUIDE + STEPS"); badge.setBackgroundColor(0xFFE0F2E9);
                card.setOnClickListener(v -> {
                    StringBuilder steps = new StringBuilder();
                    JSONArray s = o.optJSONArray("steps");
                    if (s != null) for (int k = 0; k < s.length(); k++) steps.append(k + 1).append(". ").append(s.optString(k)).append("\n\n");
                    steps.append("⚠️ ").append(o.optString("warning"));
                    new AlertDialog.Builder(getContext()).setTitle(o.optString("title")).setMessage(steps.toString())
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Open official site", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(o.optString("official_url"))))).show();
                });
                list.addView(card);
            }
        } catch (Exception e){ status.setText("Could not read the guides list."); }
    }
}
