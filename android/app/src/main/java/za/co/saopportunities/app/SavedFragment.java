package za.co.saopportunities.app;
import android.os.Bundle; import android.view.*; import android.widget.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment; import androidx.recyclerview.widget.*;
import java.util.*;
public class SavedFragment extends Fragment {
    private JobAdapter adapter; private TextView empty;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        View v = in.inflate(R.layout.fragment_saved, c, false);
        RecyclerView rv = v.findViewById(R.id.savedList); rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new JobAdapter(requireContext()); rv.setAdapter(adapter); empty = v.findViewById(R.id.savedEmpty);
        return v;
    }
    @Override public void onResume(){ super.onResume();
        Prefs p = new Prefs(requireContext()); List<Job> out = new ArrayList<>();
        String cached = p.cachedJobs();
        if (cached != null) for (Job j : Job.parseJobs(cached)) if (p.isSaved(j.id)) out.add(j);
        adapter.setItems(out); empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
