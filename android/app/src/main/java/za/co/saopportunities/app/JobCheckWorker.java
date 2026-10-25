package za.co.saopportunities.app;
import android.app.*; import android.content.*; import android.os.Build;
import androidx.annotation.NonNull; import androidx.core.app.NotificationCompat;
import androidx.work.Worker; import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets;
import java.util.*;
/** Background check (WorkManager, ~every 15 min, OS permitting):
 *  fetches jobs.json, and notifies about NEW verified jobs matching the
 *  user's alert keywords / province set in Jobs > 🔔 Alerts. */
public class JobCheckWorker extends Worker {
    public JobCheckWorker(@NonNull Context c, @NonNull WorkerParameters p){ super(c, p); }
    @NonNull @Override public Result doWork(){
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BuildConfig.JOBS_JSON_URL).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))){
                String l; while ((l = r.readLine()) != null) sb.append(l);
            }
            String body = sb.toString();
            Prefs prefs = new Prefs(getApplicationContext());
            prefs.cacheJobs(body);
            List<Job> jobs = Job.parseJobs(body);
            Set<String> notified = prefs.notifiedIds();
            boolean firstRun = notified.isEmpty();
            String kw = prefs.alertKeywords().toLowerCase(), prov = prefs.alertProvince();
            List<Job> fresh = new ArrayList<>(); List<String> freshIds = new ArrayList<>();
            for (Job j : jobs){
                if (notified.contains(j.id)) continue;
                freshIds.add(j.id);
                if (!j.verified) continue;
                if (!prov.isEmpty() && !prov.equalsIgnoreCase(j.province)) continue;
                if (!kw.isEmpty()){
                    String hay = (j.title + " " + j.company + " " + j.category + " " + j.location).toLowerCase();
                    boolean hit = false;
                    for (String k : kw.split(",")) if (!k.trim().isEmpty() && hay.contains(k.trim())) hit = true;
                    if (!hit) continue;
                }
                fresh.add(j);
            }
            prefs.markNotified(freshIds);
            if (!firstRun && !fresh.isEmpty()) notifyUser(fresh);
            return Result.success();
        } catch (Exception e){ return Result.retry(); }
    }
    private void notifyUser(List<Job> fresh){
        Context ctx = getApplicationContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(new NotificationChannel("jobs", "New verified jobs", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = "🆕 " + fresh.size() + " new verified job" + (fresh.size() > 1 ? "s" : "");
        String text = fresh.get(0).title + " — " + fresh.get(0).company + (fresh.size() > 1 ? "  (+" + (fresh.size() - 1) + " more)" : "");
        nm.notify(1001, new NotificationCompat.Builder(ctx, "jobs")
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build());
    }
}
