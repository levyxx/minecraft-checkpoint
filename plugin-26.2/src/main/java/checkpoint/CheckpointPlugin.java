package checkpoint;

import checkpoint.compat.Compat26_2;
import checkpoint.compat.VersionCompat;

public class CheckpointPlugin extends CheckpointPluginBase {
    @Override
    public void onEnable() {
        VersionCompat.init(new Compat26_2(this));
        super.onEnable();
    }
}
