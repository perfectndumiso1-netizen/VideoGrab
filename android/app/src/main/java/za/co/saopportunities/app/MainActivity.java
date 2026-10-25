package za.co.saopportunities.app;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.Manifest; import android.os.Build;
import androidx.work.*; import java.util.concurrent.TimeUnit;
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b){
        setTheme(R.style.AppTheme);
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (b == null) show(new JobsFragment());
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_new) show(new NewFragment());
            else if (id == R.id.nav_saved) show(new SavedFragment());
            else if (id == R.id.nav_videos) show(new VideosFragment());
            else if (id == R.id.nav_about) show(new AboutFragment());
            else show(new JobsFragment());
            return true;
        });
    }
        scheduleJobChecks();
    }
    private void scheduleJobChecks(){
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(JobCheckWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("job-check", ExistingPeriodicWorkPolicy.KEEP, req);
    }
    private void show(Fragment f){ getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit(); }
}
