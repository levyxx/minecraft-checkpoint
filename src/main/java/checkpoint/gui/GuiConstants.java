package checkpoint.gui;

import java.time.format.DateTimeFormatter;
import org.bukkit.ChatColor;

/**
 * Constants shared across GUI classes.
 */
public final class GuiConstants {

    public static final int GUI_SIZE       = 54;
    public static final int ITEMS_PER_PAGE = 28;
    public static final int SLOT_PREVIOUS  = 45;
    public static final int SLOT_SEARCH    = 47;
    public static final int SLOT_INFO      = 49;
    public static final int SLOT_SORT      = 51;
    public static final int SLOT_NEXT      = 53;
    public static final int SLOT_PLAYER_HEAD = 4;

    public static final String GUI_TITLE            = ChatColor.DARK_AQUA + "チェックポイント一覧";
    public static final String SORT_TITLE           = ChatColor.DARK_AQUA + "ソート方法を選択";
    public static final String PLAYER_SELECT_TITLE  = ChatColor.DARK_AQUA + "プレイヤーを選択";
    public static final String CP_OPERATION_TITLE   = ChatColor.DARK_AQUA + "CP操作";
    public static final String PLAYER_SORT_TITLE    = ChatColor.DARK_AQUA + "プレイヤーのソート方法を選択";

    public static final DateTimeFormatter CP_DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public static final int  TELEPORT_MAX_ATTEMPTS       = 5;
    public static final long TELEPORT_RETRY_DELAY_TICKS  = 1L;

    private GuiConstants() {}
}
