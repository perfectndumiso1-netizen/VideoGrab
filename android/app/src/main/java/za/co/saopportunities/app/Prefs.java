package za.co.saopportunities.app;
import android.content.*;
import java.util.*;
public class Prefs {
    private final SharedPreferences p;
    public Prefs(Context c){ p = c.getSharedPreferences("sa_opp", Context.MODE_PRIVATE); }
    public String cachedJobs(){ return p.getString("jobs_json", null); }
    public void cacheJobs(String j){ p.edit().putString("jobs_json", j).apply(); }
    public Set<String> savedIds(){ return new HashSet<>(p.getStringSet("saved", new HashSet<>())); }
    public boolean isSaved(String id){ return savedIds().contains(id); }
    public void toggleSaved(String id){ Set<String> s = savedIds(); if (s.contains(id)) s.remove(id); else s.add(id); p.edit().putStringSet("saved", s).apply(); }
}
