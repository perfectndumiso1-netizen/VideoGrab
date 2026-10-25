package za.co.saopportunities.app;
import android.os.Bundle; import android.view.*; import android.widget.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment; import androidx.recyclerview.widget.*;
import java.util.*;
/** "New" inbox: jobs that arrived since the user last marked them seen. */
public class NewFragment extends Fragment {
    private JobAdapter adapter; private TextView empty, status;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        View v = in.inflate(R.layout.fragment_new, c, false);
        RecyclerView rv = v.findViewById(R.id.newList); rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new JobAdapter(requireContext()); rv.setAdapter(adapter);
        empty = v.findViewById(R.id.newEmpty); status = v.findViewById(R.id.newStatus);
        v.findViewById(R.id.btnMarkSeen).setOnClickListener(x -> {
            Prefs p = new Prefs(requireContext()); List<String> ids = new ArrayList<>();
            String cached = p.cachedJobs();
            if (cached != null) for (Job j : Job.parseJobs(cached)) ids.add(j.id);
            p.markSeen(ids); refreshList();
        });
        return v;
    }
    @Override public void onResume(){ super.onResume(); refreshList(); }
    private void refreshList(){
        Prefs p = new Prefs(requireContext()); Set<String> seen = p.seenIds();
        List<Job> fresh = new ArrayList<>(); String cached = p.cachedJobs();
        if (cached != null) for (Job j : Job.parseJobs(cached)) if (!seen.contains(j.id)) fresh.add(j);
        fresh.sort(Comparator.comparing((Job j) -> j.category).thenComparing(j -> j.title));
        adapter.setItems(fresh);
        status.setText(fresh.isEmpty() ? "You're all caught up 🎉" : "🆕 " + fresh.size() + " job(s) since you last checked");
        empty.setVisibility(fresh.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
