package za.co.saopportunities.app;
import android.os.Bundle; import android.view.*; import android.widget.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment; import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.*;
import android.app.AlertDialog;
import java.util.*;
public class JobsFragment extends Fragment {
    private JobAdapter adapter; private TextView status, empty; private SwipeRefreshLayout refresh;
    private Spinner spinCategory, spinProvince;
    private final List<Job> all = new ArrayList<>();
    private String query = "", filter = "all", category = "All types", province = "All provinces";
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        View v = in.inflate(R.layout.fragment_jobs, c, false);
        RecyclerView rv = v.findViewById(R.id.jobList); rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new JobAdapter(requireContext()); rv.setAdapter(adapter);
        status = v.findViewById(R.id.jobStatus); empty = v.findViewById(R.id.jobEmpty);
        refresh = v.findViewById(R.id.swipe); refresh.setOnRefreshListener(this::load);
        spinCategory = v.findViewById(R.id.spinCategory); spinProvince = v.findViewById(R.id.spinProvince);
        spinCategory.setOnItemSelectedListener(new SimpleSpinner(){ public void sel(String s){ category = s; render(); }});
        spinProvince.setOnItemSelectedListener(new SimpleSpinner(){ public void sel(String s){ province = s; render(); }});
        SearchView sv = v.findViewById(R.id.search);
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            public boolean onQueryTextSubmit(String q){ return true; }
            public boolean onQueryTextChange(String q){ query = q.toLowerCase(); render(); return true; }});
        ChipGroup cg = v.findViewById(R.id.chips);
        cg.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) filter = "all";
            else { int id = ids.get(0);
                filter = id == R.id.chipVerified ? "verified" : id == R.id.chipBoard ? "board" : id == R.id.chipClosing ? "closing" : "all"; }
            render(); });
        v.findViewById(R.id.btnAlerts).setOnClickListener(x -> showAlertsDialog());
        String cached = new Prefs(requireContext()).cachedJobs();
        if (cached != null){ all.clear(); all.addAll(Job.parseJobs(cached)); setupSpinners(); render(); status.setText("Showing saved offline copy — refreshing…"); }
        load(); return v;
    }
    abstract static class SimpleSpinner implements AdapterView.OnItemSelectedListener {
        public abstract void sel(String s);
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id){ sel(String.valueOf(p.getItemAtPosition(pos))); }
        public void onNothingSelected(AdapterView<?> p){}
    }
    private void setupSpinners(){
        TreeSet<String> cats = new TreeSet<>(), provs = new TreeSet<>();
        for (Job j : all){ cats.add(j.category); if (!j.province.isEmpty()) provs.add(j.province); }
        List<String> cList = new ArrayList<>(); cList.add("All types"); cList.addAll(cats);
        List<String> pList = new ArrayList<>(); pList.add("All provinces"); pList.addAll(provs);
        setSpinner(spinCategory, cList, category); setSpinner(spinProvince, pList, province);
    }
    private void setSpinner(Spinner s, List<String> items, String keep){
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(a); int i = items.indexOf(keep); if (i >= 0) s.setSelection(i);
    }
    private void load(){
        refresh.setRefreshing(true);
        Net.get(BuildConfig.JOBS_JSON_URL, new Net.Cb(){
            public void ok(String body){
                Prefs prefs = new Prefs(requireContext());
                Set<String> before = new HashSet<>();
                String old = prefs.cachedJobs();
                if (old != null) for (Job j : Job.parseJobs(old)) before.add(j.id);
                prefs.cacheJobs(body);
                all.clear(); all.addAll(Job.parseJobs(body));
                int fresh = 0; for (Job j : all) if (!before.contains(j.id)) fresh++;
                final int newCount = (old == null) ? 0 : fresh;
                run(() -> { refresh.setRefreshing(false); setupSpinners(); render();
                    status.setText(all.size() + " SA listings grouped by type" + (newCount > 0 ? "  •  🆕 " + newCount + " new since last visit" : "") + "  •  ✅ = traced to official site"); });
            }
            public void fail(Exception e){ run(() -> { refresh.setRefreshing(false);
                status.setText(all.isEmpty() ? "No connection. Pull down to try again." : "Offline — showing your last downloaded jobs."); render(); }); }
        });
    }
    private void showAlertsDialog(){
        Prefs prefs = new Prefs(requireContext());
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(40, 20, 40, 0);
        EditText kw = new EditText(getContext()); kw.setHint("Keywords, comma separated (e.g. capitec, security, learnership)");
        kw.setText(prefs.alertKeywords()); box.addView(kw);
        EditText prov = new EditText(getContext()); prov.setHint("Province (optional, e.g. Gauteng)");
        prov.setText(prefs.alertProvince()); box.addView(prov);
        TextView note = new TextView(getContext());
        note.setText("The app checks every ±15 minutes in the background and notifies you about NEW ✅ verified jobs matching these. Leave blank to be alerted about all new verified jobs.");
        box.addView(note);
        new AlertDialog.Builder(getContext()).setTitle("🔔 Job alerts").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save alerts", (d, w) -> {
                prefs.setAlerts(kw.getText().toString(), prov.getText().toString());
                Toast.makeText(getContext(), "Alerts saved. Allow notifications if Android asks.", Toast.LENGTH_LONG).show();
            }).show();
    }
    private void run(Runnable r){ if (getActivity() != null) getActivity().runOnUiThread(r); }
    private void render(){
        if (spinCategory == null) return;
        List<Job> out = new ArrayList<>();
        for (Job j : all){
            if ("verified".equals(filter) && !j.verified) continue;
            if ("board".equals(filter) && j.verified) continue;
            if ("closing".equals(filter) && !j.closingSoon()) continue;
            if (!"All types".equals(category) && !category.equals(j.category)) continue;
            if (!"All provinces".equals(province) && !province.equals(j.province)) continue;
            String hay = (j.title + " " + j.company + " " + j.category + " " + j.location).toLowerCase();
            if (!query.isEmpty() && !hay.contains(query)) continue;
            out.add(j);
        }
        if ("closing".equals(filter)){
            out.sort(Comparator.comparingLong(Job::daysUntilClosing));
            adapter.setItems(out);
        } else adapter.setGrouped(out);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        if (out.isEmpty()) empty.setText(all.isEmpty()
            ? "No jobs published yet.\nThe scanner runs every 15 minutes, all day.\nPull down to refresh."
            : "No jobs match your search/filters.\nTry 'All types' and 'All provinces'.");
    }
}
