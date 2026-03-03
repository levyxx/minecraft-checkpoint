package checkpoint.gui;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    public static final int SLOT_DISPLAY_MODE = 2;  // top row, col 2
    public static final int SLOT_CLEAR_SORT  = 6;   // top row, col 6

    // Title sets for matching (both JP and EN)
    private static final Set<String> GUI_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "チェックポイント一覧",
        ChatColor.DARK_AQUA + "Checkpoint List")));
    private static final Set<String> SORT_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "ソート方法を選択",
        ChatColor.DARK_AQUA + "Select Sort Order")));
    private static final Set<String> PLAYER_SELECT_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "プレイヤーを選択",
        ChatColor.DARK_AQUA + "Select Player")));
    private static final Set<String> CP_OPERATION_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "CP操作",
        ChatColor.DARK_AQUA + "CP Operations")));
    private static final Set<String> PLAYER_SORT_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "プレイヤーのソート方法を選択",
        ChatColor.DARK_AQUA + "Select Player Sort Order")));
    private static final Set<String> CLEAR_SORT_TITLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ChatColor.DARK_AQUA + "クリアソート",
        ChatColor.DARK_AQUA + "Clear Sort")));

    public static boolean isGuiTitle(String title)            { return GUI_TITLES.contains(title); }
    public static boolean isSortTitle(String title)           { return SORT_TITLES.contains(title); }
    public static boolean isPlayerSelectTitle(String title)   { return PLAYER_SELECT_TITLES.contains(title); }
    public static boolean isCpOperationTitle(String title)    { return CP_OPERATION_TITLES.contains(title); }
    public static boolean isPlayerSortTitle(String title)     { return PLAYER_SORT_TITLES.contains(title); }
    public static boolean isClearSortTitle(String title)      { return CLEAR_SORT_TITLES.contains(title); }

    public static boolean isOurMenu(String title) {
        return isGuiTitle(title) || isSortTitle(title) || isPlayerSelectTitle(title)
            || isCpOperationTitle(title) || isPlayerSortTitle(title) || isClearSortTitle(title);
    }

    public static final DateTimeFormatter CP_DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public static final int  TELEPORT_MAX_ATTEMPTS       = 5;
    public static final long TELEPORT_RETRY_DELAY_TICKS  = 1L;

    private GuiConstants() {}
}
