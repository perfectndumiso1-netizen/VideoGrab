package za.co.saopportunities.app;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b){
        setTheme(R.style.AppTheme);
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (b == null) show(new JobsFragment());
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_saved) show(new SavedFragment());
            else if (id == R.id.nav_videos) show(new VideosFragment());
            else if (id == R.id.nav_about) show(new AboutFragment());
            else show(new JobsFragment());
            return true;
        });
    }
    private void show(Fragment f){ getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit(); }
}
