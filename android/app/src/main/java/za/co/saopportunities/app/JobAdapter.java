package za.co.saopportunities.app;
import android.content.*; import android.view.*; import android.widget.*;
import androidx.annotation.NonNull; import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
public class JobAdapter extends RecyclerView.Adapter<JobAdapter.H> {
    private List<Job> items = new ArrayList<>();
    private final Context ctx;
    public JobAdapter(Context c){ ctx = c; }
    public void setItems(List<Job> list){ items = list; notifyDataSetChanged(); }
    @NonNull @Override public H onCreateViewHolder(@NonNull ViewGroup parent, int vt){
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_job, parent, false); return new H(v);
    }
    @Override public void onBindViewHolder(@NonNull H h, int pos){
        Job j = items.get(pos);
        h.title.setText(j.title); h.company.setText(j.company);
        h.meta.setText(j.sourceName + (j.confidence > 0 ? "  •  Confidence " + j.confidence + "%" : ""));
        if (j.verified){ h.badge.setText("✅ VERIFIED — official site"); h.badge.setBackgroundColor(0xFFE0F2E9); }
        else { h.badge.setText("⚠️ BOARD LISTING — not yet traced"); h.badge.setBackgroundColor(0xFFFFF3CD); }
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, JobDetailActivity.class); i.putExtra("job", j.raw); ctx.startActivity(i);
        });
    }
    @Override public int getItemCount(){ return items.size(); }
    static class H extends RecyclerView.ViewHolder {
        TextView title, company, meta, badge;
        H(View v){ super(v); title = v.findViewById(R.id.jobTitle); company = v.findViewById(R.id.jobCompany);
            meta = v.findViewById(R.id.jobMeta); badge = v.findViewById(R.id.jobBadge); }
    }
}
