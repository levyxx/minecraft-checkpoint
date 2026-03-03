package checkpoint;

import checkpoint.compat.Compat1_12;
import checkpoint.compat.VersionCompat;

public class CheckpointPlugin extends CheckpointPluginBase {
    @Override
    public void onEnable() {
        VersionCompat.init(new Compat1_12());
        super.onEnable();
    }
}
