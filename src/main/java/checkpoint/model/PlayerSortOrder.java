package checkpoint.model;

/**
 * Sorting orders for the player selection list.
 */
public enum PlayerSortOrder {
    NAME_ASC("名前順（昇順）"),
    NAME_DESC("名前順（降順）"),
    CLONED_BY_ME_DESC("クローンした順"),
    CLONED_COUNT_DESC("被クローン数順"),
    DISTANCE_ASC("距離（近い順）"),
    LAST_ACTIVITY_DESC("最終操作（新しい順）"),
    LAST_ACTIVITY_ASC("最終操作（古い順）");

    public final String label;

    PlayerSortOrder(String label) {
        this.label = label;
    }
}
