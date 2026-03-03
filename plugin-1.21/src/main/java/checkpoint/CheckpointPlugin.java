package checkpoint;

import checkpoint.compat.Compat1_21;
import checkpoint.compat.VersionCompat;

public class CheckpointPlugin extends CheckpointPluginBase {
    @Override
    public void onEnable() {
        VersionCompat.init(new Compat1_21(this));
        super.onEnable();
    }
}
