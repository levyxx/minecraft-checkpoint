package checkpoint.model;

/**
 * Sorting orders for checkpoint lists.
 */
public enum SortOrder {
    NAME_ASC("名前順（昇順）"),
    NAME_DESC("名前順（降順）"),
    CREATED_ASC("作成日時（古い順）"),
    CREATED_DESC("作成日時（新しい順）"),
    UPDATED_ASC("更新日時（古い順）"),
    UPDATED_DESC("更新日時（新しい順）"),
    DISTANCE_ASC("距離（近い順）");

    public final String label;

    SortOrder(String label) {
        this.label = label;
    }
}
