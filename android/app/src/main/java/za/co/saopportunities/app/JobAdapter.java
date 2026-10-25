package za.co.saopportunities.app;
import android.content.*; import android.view.*; import android.widget.*;
import androidx.annotation.NonNull; import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
/** Grouped list: category header rows (e.g. "🛒 Cashiers & Retail (4)") followed by that group's jobs. */
public class JobAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<Object> rows = new ArrayList<>(); // String header or Job
    private final Context ctx;
    public JobAdapter(Context c){ ctx = c; }
    public void setGrouped(List<Job> jobs){
        rows.clear();
        Map<String, List<Job>> groups = new LinkedHashMap<>();
        List<Job> sorted = new ArrayList<>(jobs);
        sorted.sort(Comparator.comparing((Job j) -> j.category).thenComparing(j -> j.title));
        for (Job j : sorted) groups.computeIfAbsent(j.category, k -> new ArrayList<>()).add(j);
        for (Map.Entry<String, List<Job>> e : groups.entrySet()){
            rows.add(e.getKey() + "  (" + e.getValue().size() + ")");
            rows.addAll(e.getValue());
        }
        notifyDataSetChanged();
    }
    public void setItems(List<Job> list){ rows.clear(); rows.addAll(list); notifyDataSetChanged(); }
    @Override public int getItemViewType(int pos){ return rows.get(pos) instanceof String ? 0 : 1; }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt){
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (vt == 0){ View v = inf.inflate(R.layout.item_header, parent, false); return new HeaderH(v); }
        return new H(inf.inflate(R.layout.item_job, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos){
        Object row = rows.get(pos);
        if (holder instanceof HeaderH){ ((HeaderH) holder).title.setText("📂 " + row); return; }
        H h = (H) holder; Job j = (Job) row;
        h.title.setText(j.title); h.company.setText(j.company);
        StringBuilder meta = new StringBuilder();
        if (!j.location.isEmpty()) meta.append("📍 ").append(j.location).append("   ");
        if (!j.jobType.isEmpty()) meta.append(j.jobType).append("   ");
        if (!j.closingDate.isEmpty()) meta.append(j.closingSoon() ? "🔴 " : "⏰ ").append(j.closingLabel().isEmpty() ? "Closes: " + j.closingDate : j.closingLabel()).append("   ");
        if (!j.salary.isEmpty()) meta.append("💰 ").append(j.salary);
        h.meta.setText(meta.length() > 0 ? meta.toString().trim() : j.sourceName);
        h.meta.setTextColor(j.closingSoon() ? 0xFFB00020 : 0xFF5A6B62);
        if (j.verified){ h.badge.setText("✅ VERIFIED — official site"); h.badge.setBackgroundColor(0xFFE0F2E9); }
        else { h.badge.setText("⚠️ BOARD LISTING — not yet traced"); h.badge.setBackgroundColor(0xFFFFF3CD); }
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, JobDetailActivity.class); i.putExtra("job", j.raw); ctx.startActivity(i);
        });
    }
    @Override public int getItemCount(){ return rows.size(); }
    static class H extends RecyclerView.ViewHolder {
        TextView title, company, meta, badge;
        H(View v){ super(v); title = v.findViewById(R.id.jobTitle); company = v.findViewById(R.id.jobCompany);
            meta = v.findViewById(R.id.jobMeta); badge = v.findViewById(R.id.jobBadge); }
    }
    static class HeaderH extends RecyclerView.ViewHolder {
        TextView title; HeaderH(View v){ super(v); title = v.findViewById(R.id.headerTitle); }
    }
}
