package za.co.saopportunities.app;
import android.content.Intent; import android.net.Uri; import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
public class JobDetailActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_detail);
        Job j;
        try { j = Job.from(new JSONObject(getIntent().getStringExtra("job"))); }
        catch (Exception e){ finish(); return; }
        ((TextView) findViewById(R.id.dTitle)).setText(j.title);
        ((TextView) findViewById(R.id.dCompany)).setText(j.company);
        TextView badge = findViewById(R.id.dBadge);
        badge.setText(j.verified ? "✅ VERIFIED — confirmed on the employer's official website" : "⚠️ BOARD LISTING — not yet traced to the employer. Be careful.");
        badge.setBackgroundColor(j.verified ? 0xFFE0F2E9 : 0xFFFFF3CD);
        StringBuilder meta = new StringBuilder();
        meta.append("📂 ").append(j.category);
        if (!j.location.isEmpty()) meta.append("   📍 ").append(j.location);
        if (!j.jobType.isEmpty()) meta.append("   🕐 ").append(j.jobType);
        if (!j.closingDate.isEmpty()) meta.append("\n⏰ Closing date: ").append(j.closingDate);
        if (!j.salary.isEmpty()) meta.append("\n💰 Salary: ").append(j.salary);
        ((TextView) findViewById(R.id.dMeta)).setText(meta.toString());
        StringBuilder s = new StringBuilder();
        if (!j.description.isEmpty()) s.append("📋 JOB DESCRIPTION\n").append(j.description).append("\n\n");
        if (!j.requirements.isEmpty()) s.append("🎓 REQUIREMENTS\n").append(j.requirements).append("\n\n");
        if (j.description.isEmpty() && j.requirements.isEmpty())
            s.append("Full description and requirements are on the employer's page — tap the Apply button below to read them on the official site.\n\n");
        if (j.confidence > 0) s.append("Confidence: ").append(j.confidence).append("%\n\n");
        s.append("Checks done by SA Opportunities:\n");
        for (String c : j.checks) s.append("• ").append(c).append("\n");
        if (!j.warnings.isEmpty()){ s.append("\nWarnings:\n"); for (String w : j.warnings) s.append("• ").append(w).append("\n"); }
        s.append("\nSource: ").append(j.sourceName).append("\n\n🚫 Never pay anyone to apply for a job. Real employers do not charge applicants.");
        ((TextView) findViewById(R.id.dBody)).setText(s.toString());
        Button apply = findViewById(R.id.btnApply);
        apply.setText(j.verified ? "Apply on official site" : "Open listing");
        apply.setOnClickListener(v -> { if (!j.applyUrl.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(j.applyUrl))); });
        Button save = findViewById(R.id.btnSave);
        Prefs p = new Prefs(this);
        save.setText(p.isSaved(j.id) ? "★ Saved — tap to remove" : "☆ Save this job");
        save.setOnClickListener(v -> { p.toggleSaved(j.id); save.setText(p.isSaved(j.id) ? "★ Saved — tap to remove" : "☆ Save this job"); });
        findViewById(R.id.btnShare).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND); share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, j.title + " at " + j.company + (j.verified ? " (✅ verified on the official site)" : "") + "\n" + j.applyUrl + "\n— via SA Opportunities");
            startActivity(Intent.createChooser(share, "Share job"));
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
