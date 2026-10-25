package za.co.saopportunities.app;
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets;
public class Net {
    public interface Cb { void ok(String body); void fail(Exception e); }
    public static void get(String url, Cb cb){
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestProperty("Accept", "application/json");
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) sb.append(l);
                }
                cb.ok(sb.toString());
            } catch (Exception e){ cb.fail(e); }
        }).start();
    }
}
