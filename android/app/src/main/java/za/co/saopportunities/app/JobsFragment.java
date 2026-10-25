package za.co.saopportunities.app;
import android.os.Bundle; import android.view.*; import android.widget.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment; import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.*;
import java.util.*;
public class JobsFragment extends Fragment {
    private JobAdapter adapter; private TextView status, empty; private SwipeRefreshLayout refresh;
    private final List<Job> all = new ArrayList<>();
    private String query = "", filter = "all";
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        View v = in.inflate(R.layout.fragment_jobs, c, false);
        RecyclerView rv = v.findViewById(R.id.jobList); rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new JobAdapter(requireContext()); rv.setAdapter(adapter);
        status = v.findViewById(R.id.jobStatus); empty = v.findViewById(R.id.jobEmpty);
        refresh = v.findViewById(R.id.swipe); refresh.setOnRefreshListener(this::load);
        SearchView sv = v.findViewById(R.id.search);
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            public boolean onQueryTextSubmit(String q){ return true; }
            public boolean onQueryTextChange(String q){ query = q.toLowerCase(); render(); return true; }});
        ChipGroup cg = v.findViewById(R.id.chips);
        cg.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) filter = "all";
            else { int id = ids.get(0);
                filter = id == R.id.chipVerified ? "verified" : id == R.id.chipBoard ? "board" : "all"; }
            render(); });
        String cached = new Prefs(requireContext()).cachedJobs();
        if (cached != null){ all.clear(); all.addAll(Job.parseJobs(cached)); render(); status.setText("Showing saved offline copy — refreshing…"); }
        load(); return v;
    }
    private void load(){
        refresh.setRefreshing(true);
        Net.get(BuildConfig.JOBS_JSON_URL, new Net.Cb(){
            public void ok(String body){
                new Prefs(requireContext()).cacheJobs(body);
                all.clear(); all.addAll(Job.parseJobs(body));
                run(() -> { refresh.setRefreshing(false); render(); status.setText(all.size() + " South African listings • ✅ = traced to employer's official site"); });
            }
            public void fail(Exception e){ run(() -> { refresh.setRefreshing(false);
                status.setText(all.isEmpty() ? "No connection. Pull down to try again." : "Offline — showing your last downloaded jobs."); render(); }); }
        });
    }
    private void run(Runnable r){ if (getActivity() != null) getActivity().runOnUiThread(r); }
    private void render(){
        List<Job> out = new ArrayList<>();
        for (Job j : all){
            if ("verified".equals(filter) && !j.verified) continue;
            if ("board".equals(filter) && j.verified) continue;
            if (!query.isEmpty() && !(j.title + " " + j.company).toLowerCase().contains(query)) continue;
            out.add(j);
        }
        adapter.setItems(out);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        if (out.isEmpty()) empty.setText(all.isEmpty()
            ? "No jobs published yet.\nThe scanner runs at 07:00, 13:00 and 19:00 (SA time).\nPull down to refresh."
            : "No jobs match your search/filter.");
    }
}
