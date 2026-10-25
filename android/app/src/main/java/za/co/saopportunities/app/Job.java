package za.co.saopportunities.app;
import org.json.*;
import java.util.*;
public class Job {
    public String id, title, company, sourceName, applyUrl, raw;
    public String category = "Other Jobs", location = "", province = "", closingDate = "", salary = "", jobType = "";
    public String description = "", requirements = "";
    public boolean verified; public int confidence;
    public List<String> checks = new ArrayList<>(), warnings = new ArrayList<>();
    public static Job from(JSONObject o){
        Job j = new Job(); j.raw = o.toString();
        j.id = o.optString("id"); j.title = cleanTitle(o.optString("title", "Vacancy"));
        j.company = o.optString("company", "See listing"); j.sourceName = o.optString("source_name", "");
        j.applyUrl = o.optString("apply_url", "");
        j.category = o.optString("category", "Other Jobs"); if (j.category.isEmpty()) j.category = "Other Jobs";
        j.location = o.optString("location", ""); j.province = o.optString("province", "");
        j.closingDate = o.optString("closing_date", ""); j.salary = o.optString("salary", "");
        j.jobType = o.optString("job_type", ""); j.description = o.optString("description", "");
        j.requirements = o.optString("requirements", "");
        JSONObject v = o.optJSONObject("verification");
        if (v != null){ j.verified = "verified_official".equals(v.optString("status")); j.confidence = v.optInt("confidence");
            JSONArray c = v.optJSONArray("checks"); if (c != null) for (int i=0;i<c.length();i++) j.checks.add(c.optString(i));
            JSONArray w = v.optJSONArray("warnings"); if (w != null) for (int i=0;i<w.length();i++) j.warnings.add(w.optString(i)); }
        return j;
    }
    /** Tidy scraped titles: collapse whitespace, trim trailing site names. */
    public static String cleanTitle(String t){
        String s = t.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(?i)\\s*[-|]\\s*(careers24|pnet|indeed).*$", "");
        return s.length() > 90 ? s.substring(0, 90).trim() + "…" : s;
    }
    public static List<Job> parseJobs(String json){
        List<Job> out = new ArrayList<>();
        try { JSONArray a = new JSONObject(json).optJSONArray("jobs");
            if (a != null) for (int i=0;i<a.length();i++) out.add(from(a.getJSONObject(i))); }
        catch (Exception ignored){}
        return out;
    }
}
