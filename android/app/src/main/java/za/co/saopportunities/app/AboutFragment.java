package za.co.saopportunities.app;
import android.os.Bundle; import android.view.*;
import androidx.annotation.*; import androidx.fragment.app.Fragment;
public class AboutFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater in, @Nullable ViewGroup c, @Nullable Bundle b){
        return in.inflate(R.layout.fragment_about, c, false);
    }
}
