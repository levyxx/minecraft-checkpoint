package checkpoint.i18n;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;

/**
 * Centralized localization system. Holds all user-facing strings in
 * Japanese (jp) and English (en). Each player's language preference is
 * stored in memory and defaults based on their Minecraft client locale.
 */
public final class Messages {

    public enum Lang { JP, EN }

    private static final Map<UUID, Lang> playerLangs  = new ConcurrentHashMap<>();
    /** Players who explicitly set their language via /cp language. Persisted across sessions. */
    private static final Map<UUID, Lang> manualLangs  = new ConcurrentHashMap<>();

    private Messages() {}

    // -----------------------------------------------------------------------
    // Player language management
    // -----------------------------------------------------------------------

    /** Set language (auto-detected). Does NOT override a manually set preference. */
    public static void setLang(UUID playerId, Lang lang) { playerLangs.put(playerId, lang); }

    /** Set language explicitly by the player. Marks it as manually set. */
    public static void setLangManual(UUID playerId, Lang lang) {
        manualLangs.put(playerId, lang);
        playerLangs.put(playerId, lang);
    }

    /** Returns true if the player has manually set their language. */
    public static boolean isManuallySet(UUID playerId) { return manualLangs.containsKey(playerId); }

    /** Returns the manually set language, or null if not manually set. */
    public static Lang getManualLang(UUID playerId) { return manualLangs.get(playerId); }

    /**
     * Load a persisted manual language preference (called on plugin enable or player join).
     * Does not affect playerLangs — call setLang separately to activate it.
     */
    public static void loadManualLang(UUID playerId, Lang lang) { manualLangs.put(playerId, lang); }

    public static Lang getLang(UUID playerId) { return playerLangs.getOrDefault(playerId, Lang.JP); }

    /** Remove in-session language state. Manual preference is intentionally kept. */
    public static void removeLang(UUID playerId) { playerLangs.remove(playerId); }

    /** Clear all in-session state. Manual preferences are intentionally kept for persistence. */
    public static void clearAll() { playerLangs.clear(); }

    /** Clear all state including manual preferences (used for testing). */
    public static void clearAllIncludingManual() { playerLangs.clear(); manualLangs.clear(); }

    /** Determine default language from Minecraft client locale string. */
    public static Lang detectLang(String locale) {
        if (locale != null && locale.toLowerCase().startsWith("ja")) return Lang.JP;
        return Lang.EN;
    }

    // -----------------------------------------------------------------------
    // Helper: pick JP/EN string
    // -----------------------------------------------------------------------

    public static String get(UUID playerId, String jp, String en) {
        return getLang(playerId) == Lang.JP ? jp : en;
    }

    // -----------------------------------------------------------------------
    // Plugin enable/disable
    // -----------------------------------------------------------------------

    public static String pluginEnabled(Lang l) {
        return l == Lang.JP ? "Checkpoint plugin enabled." : "Checkpoint plugin enabled.";
    }

    // -----------------------------------------------------------------------
    // Command messages
    // -----------------------------------------------------------------------

    public static String cmdPlayerOnly(UUID id)    { return get(id, "このコマンドはプレイヤーのみ使用できます。", "This command can only be used by players."); }
    public static String cmdEnterCpName(UUID id)   { return get(id, "チェックポイント名を入力してください。", "Please enter a checkpoint name."); }
    public static String cmdUsageRename(UUID id, String l) { return get(id, "使い方: /" + l + " rename <元のCP名> -n <変更後のCP名>", "Usage: /" + l + " rename <old-name> -n <new-name>"); }
    public static String cmdUsageDesc(UUID id, String l)   { return get(id, "使い方: /" + l + " description <CP名> -d <説明>", "Usage: /" + l + " description <name> -d <description>"); }
    public static String cmdUsageLanguage(UUID id, String l) { return get(id, "使い方: /" + l + " language <ja|en>", "Usage: /" + l + " language <ja|en>"); }
    public static String cmdUsage(UUID id, String l) { return get(id,
        "/" + l + " help" + " でコマンド一覧を確認できます。",
        "Run /" + l + " help to see available commands."); }

    public static String cmdWorldError(UUID id)    { return get(id, "ワールド情報が取得できませんでした。", "Could not get world information."); }

    public static String cmdSetSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を保存しました。", "Checkpoint '" + n + "' saved."); }
    public static String cmdSetDuplicate(UUID id, String n) { return get(id, "チェックポイント『" + n + "』は既に存在します。", "Checkpoint '" + n + "' already exists."); }
    public static String cmdDescSuccess(UUID id, String n) { return get(id, "『" + n + "』の説明を設定しました。", "Description set for '" + n + "'."); }
    public static String cmdDescNotFound(UUID id, String n) { return get(id, "チェックポイント『" + n + "』が見つかりませんでした。", "Checkpoint '" + n + "' not found."); }
    public static String cmdUpdateSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を更新しました。", "Checkpoint '" + n + "' updated."); }
    public static String cmdUpdateNotFound(UUID id, String n) { return get(id, "チェックポイント『" + n + "』は存在しません。", "Checkpoint '" + n + "' does not exist."); }
    public static String cmdDeleteSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を削除しました。", "Checkpoint '" + n + "' deleted."); }
    public static String cmdDeleteNotFound(UUID id, String n) { return get(id, "チェックポイント『" + n + "』は見つかりませんでした。", "Checkpoint '" + n + "' not found."); }
    public static String cmdRenameSuccess(UUID id, String o, String n) { return get(id, "チェックポイント『" + o + "』を『" + n + "』に変更しました。", "Checkpoint '" + o + "' renamed to '" + n + "'."); }
    public static String cmdRenameOldNotFound(UUID id, String o) { return get(id, "チェックポイント『" + o + "』は見つかりませんでした。", "Checkpoint '" + o + "' not found."); }
    public static String cmdRenameNewExists(UUID id, String n) { return get(id, "チェックポイント『" + n + "』は既に存在します。", "Checkpoint '" + n + "' already exists."); }
    public static String cmdLangChanged(UUID id) { return get(id, "言語を日本語に変更しました。", "Language changed to English."); }

    // -----------------------------------------------------------------------
    // Help messages
    // -----------------------------------------------------------------------

    private static final String HC = ChatColor.YELLOW.toString();   // command color
    private static final String HD = ChatColor.GRAY.toString();     // description color

    public static String helpTitle(UUID id) { return get(id, "コマンド一覧", "Command List"); }
    public static String helpSet(UUID id, String l) { return get(id,
        HC + "/" + l + " set <名前> [-d 説明]" + HD + "  現在地を指定した名前で保存（-d で説明追加）",
        HC + "/" + l + " set <name> [-d desc]" + HD + "  Save current location with a name (-d for description)"); }
    public static String helpUpdate(UUID id, String l) { return get(id,
        HC + "/" + l + " update <名前>" + HD + "  既存CPの座標を現在地で上書きします",
        HC + "/" + l + " update <name>" + HD + "  Update existing CP coordinates to current location"); }
    public static String helpDelete(UUID id, String l) { return get(id,
        HC + "/" + l + " delete <名前>" + HD + "  指定したCPを削除します",
        HC + "/" + l + " delete <name>" + HD + "  Delete specified CP"); }
    public static String helpRename(UUID id, String l) { return get(id,
        HC + "/" + l + " rename <元の名前> -n <新しい名前>" + HD + "  CPの名前を変更します",
        HC + "/" + l + " rename <old> -n <new>" + HD + "  Rename a CP"); }
    public static String helpDescription(UUID id, String l) { return get(id,
        HC + "/" + l + " description <名前> -d <説明>" + HD + "  CPに説明を設定します",
        HC + "/" + l + " description <name> -d <desc>" + HD + "  Set CP description"); }
    public static String helpItems(UUID id, String l) { return get(id,
        HC + "/" + l + " items" + HD + "  チェックポイント用アイテムを受け取ります",
        HC + "/" + l + " items" + HD + "  Receive checkpoint utility items"); }
    public static String helpLanguage(UUID id, String l) { return get(id,
        HC + "/" + l + " language <ja|en>" + HD + "  言語を変更します",
        HC + "/" + l + " language <ja|en>" + HD + "  Change language"); }
    public static String helpHelp(UUID id, String l) { return get(id,
        HC + "/" + l + " help" + HD + "  このヘルプを表示します",
        HC + "/" + l + " help" + HD + "  Show this help"); }

    // -----------------------------------------------------------------------
    // Give items
    // -----------------------------------------------------------------------

    public static String itemsReceived(UUID id) { return get(id,
        "チェックポイントアイテムを受け取りました。所持品を確認してください。",
        "Checkpoint items received. Check your inventory."); }
    public static String itemNetherStarLoreL(UUID id) { return get(id, "左クリック: チェックポイント一覧", "Left-click: Checkpoint list"); }
    public static String itemNetherStarLoreR(UUID id) { return get(id, "右クリック: テレポート", "Right-click: Teleport"); }
    public static String itemSlimeLore(UUID id) { return get(id, "右クリック: 現在地を保存", "Right-click: Save current location"); }
    public static String itemFeatherLore(UUID id) { return get(id, "右クリック: クリエ/アドベンチャー切替", "Right-click: Toggle Creative/Adventure"); }
    public static String itemHeartLore(UUID id) { return get(id, "右クリック: チェックポイント一覧", "Right-click: Checkpoint list"); }

    // -----------------------------------------------------------------------
    // Quick checkpoint (slime ball) / Nether star teleport
    // -----------------------------------------------------------------------

    public static String quickSaved(UUID id) { return get(id, "チェックポイントを保存しました！", "Checkpoint saved!"); }
    public static String noCheckpoint(UUID id) { return get(id, "チェックポイントがまだ登録されていません。", "No checkpoint registered yet."); }
    public static String worldNotFound(UUID id) { return get(id, "チェックポイントのワールドが見つかりませんでした。", "Checkpoint world not found."); }
    public static String teleportFailed(UUID id) { return get(id, "テレポートに失敗しました。", "Teleport failed."); }

    // -----------------------------------------------------------------------
    // GUI titles
    // -----------------------------------------------------------------------

    public static String guiTitle(UUID id)           { return get(id, "チェックポイント一覧", "Checkpoint List"); }
    public static String sortTitle(UUID id)          { return get(id, "ソート方法を選択", "Select Sort Order"); }
    public static String playerSelectTitle(UUID id)  { return get(id, "プレイヤーを選択", "Select Player"); }
    public static String cpOperationTitle(UUID id)   { return get(id, "CP操作", "CP Operations"); }
    public static String playerSortTitle(UUID id)    { return get(id, "プレイヤーのソート方法を選択", "Select Player Sort Order"); }

    // -----------------------------------------------------------------------
    // CP list GUI items
    // -----------------------------------------------------------------------

    public static String cpWorld(UUID id)       { return get(id, "ワールド: ", "World: "); }
    public static String cpCreated(UUID id)     { return get(id, "作成: ", "Created: "); }
    public static String cpUpdated(UUID id)     { return get(id, "更新: ", "Updated: "); }
    public static String cpCurrentSelection(UUID id) { return get(id, "現在選択中", "Currently selected"); }
    public static String cpLeftSelectRight(UUID id)  { return get(id, "左クリックで選択 / 右クリックで操作", "Left-click: Select / Right-click: Operations"); }
    public static String cpLeftTpRight(UUID id)      { return get(id, "左クリックでテレポート / 右クリックで操作", "Left-click: Teleport / Right-click: Operations"); }

    // Navigation
    public static String navNext(UUID id)           { return get(id, "次のページ", "Next Page"); }
    public static String navPrevious(UUID id)       { return get(id, "前のページ", "Previous Page"); }
    public static String navPage(UUID id)           { return get(id, "ページ: ", "Page: "); }
    public static String navLeftClickMove(UUID id)  { return get(id, "左クリックで移動", "Left-click to navigate"); }
    public static String navNoPrev(UUID id)         { return get(id, "前のページなし", "No previous page"); }
    public static String navNoNext(UUID id)         { return get(id, "次のページなし", "No next page"); }
    public static String navNoMove(UUID id)         { return get(id, "ページ移動はできません", "Cannot navigate"); }

    // Info item
    public static String infoTitle(UUID id)         { return get(id, "ページ情報", "Page Info"); }
    public static String infoCount(UUID id)         { return get(id, "表示数: ", "Items: "); }
    public static String infoSort(UUID id)          { return get(id, "ソート: ", "Sort: "); }
    public static String infoSearch(UUID id)        { return get(id, "検索: ", "Search: "); }
    public static String infoClickHint(UUID id)     { return get(id, "紙を左クリックで選択", "Left-click a paper to select"); }

    // Sort button
    public static String sortChange(UUID id)        { return get(id, "ソート方法を変更", "Change Sort Order"); }
    public static String sortCurrent(UUID id)       { return get(id, "現在: ", "Current: "); }
    public static String sortOpenSelect(UUID id)    { return get(id, "左クリックで選択画面を開く", "Left-click to open selection"); }
    public static String sortCurrentMark(UUID id)   { return get(id, "✔ 現在選択中", "✔ Currently selected"); }
    public static String sortLeftClick(UUID id)     { return get(id, "左クリックで選択", "Left-click to select"); }

    // Search item
    public static String searchCpName(UUID id)      { return get(id, "CP名を検索", "Search CP Name"); }
    public static String searchOpen(UUID id)        { return get(id, "左クリックで検索バーを開く", "Left-click to open search"); }
    public static String searchClear(UUID id)       { return get(id, "右クリックで検索を解除", "Right-click to clear search"); }
    public static String searchPartial(UUID id)     { return get(id, "部分一致でフィルタリングします", "Filters by partial match"); }

    // Empty notice
    public static String emptyTitle(UUID id)        { return get(id, "チェックポイントがありません", "No Checkpoints"); }
    public static String emptyHint1(UUID id)        { return get(id, "コマンド /cp set <名前> で登録できます", "Use /cp set <name> to register"); }
    public static String emptyHint2(UUID id)        { return get(id, "登録後に海洋の心をクリックすると表示されます", "Click Heart of the Sea after registering"); }

    // Player head in CP list
    public static String headSelf(UUID id, String name)   { return get(id, "自分（" + name + "）", "You (" + name + ")"); }
    public static String headOther(UUID id, String name)   { return get(id, name + " のCP", name + "'s CPs"); }
    public static String headClickChange(UUID id)          { return get(id, "クリックでプレイヤーを変更", "Click to change player"); }
    public static String headViewingSelf(UUID id)          { return get(id, "現在: 自分のCPを表示中", "Currently: Viewing your CPs"); }
    public static String headViewingOther(UUID id, String n) { return get(id, "現在: " + n + " のCPを表示中", "Currently: Viewing " + n + "'s CPs"); }

    // CP selected
    public static String cpSelected(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を選択しました。", "Checkpoint '" + n + "' selected."); }

    // -----------------------------------------------------------------------
    // CP operation menu
    // -----------------------------------------------------------------------

    public static String opTeleport(UUID id)     { return get(id, "テレポート", "Teleport"); }
    public static String opTeleportLore(UUID id) { return get(id, "クリックでこのCPにテレポート", "Click to teleport to this CP"); }
    public static String opUpdate(UUID id)       { return get(id, "座標を更新", "Update Coords"); }
    public static String opUpdateLore(UUID id)   { return get(id, "現在の座標でこのCPを上書き", "Overwrite this CP with current location"); }
    public static String opRename(UUID id)       { return get(id, "リネーム", "Rename"); }
    public static String opRenameLore(UUID id)   { return get(id, "このCPの名前を変更", "Change this CP's name"); }
    public static String opDescChange(UUID id)   { return get(id, "説明を変更", "Change Description"); }
    public static String opDescChangeLore(UUID id) { return get(id, "このCPの説明文を変更", "Change this CP's description"); }
    public static String opDelete(UUID id)       { return get(id, "削除", "Delete"); }
    public static String opDeleteLore(UUID id)   { return get(id, "このCPを削除する", "Delete this CP"); }
    public static String opClone(UUID id)        { return get(id, "クローン", "Clone"); }
    public static String opCloneLore(UUID id, String name) { return get(id, name + " のCPを自分のリストに追加", "Add " + name + "'s CP to your list"); }
    public static String opClickExecute(UUID id) { return get(id, "クリックで実行", "Click to execute"); }

    // CP operation results
    public static String cpNotFound(UUID id)             { return get(id, "CPが見つかりません。", "CP not found."); }
    public static String cpUpdateSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を現在地に更新しました。", "Checkpoint '" + n + "' updated to current location."); }
    public static String cpUpdateFailed(UUID id)         { return get(id, "更新に失敗しました。", "Update failed."); }
    public static String cpDeleteSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』を削除しました。", "Checkpoint '" + n + "' deleted."); }
    public static String cpDeleteFailed(UUID id)         { return get(id, "削除に失敗しました。", "Deletion failed."); }
    public static String cpCloneSuccess(UUID id, String n) { return get(id, "チェックポイント『" + n + "』をクローンしました。", "Checkpoint '" + n + "' cloned."); }
    public static String cpCloneDuplicate(UUID id, String n) { return get(id, "同名のCPが既に存在します: 『" + n + "』", "A CP with the same name already exists: '" + n + "'"); }

    // -----------------------------------------------------------------------
    // Chat input prompts
    // -----------------------------------------------------------------------

    public static String searchPromptTitle(UUID id)   { return get(id, "チェックポイント検索", "Checkpoint Search"); }
    public static String searchPromptMsg(UUID id)     { return get(id, "検索したいCP名をチャットに入力してください。", "Type a CP name in chat to search."); }
    public static String searchCancel(UUID id)        { return get(id, "で取消", "to cancel"); }
    public static String searchCancelled(UUID id)     { return get(id, "検索をキャンセルしました。", "Search cancelled."); }
    public static String searchCleared(UUID id)       { return get(id, "検索を解除しました。", "Search cleared."); }
    public static String searchSearching(UUID id, String q) { return get(id, "「" + q + "」で検索中...", "Searching for '" + q + "'..."); }

    public static String renamePromptTitle(UUID id, String n) { return get(id, "CP リネーム: " + n, "CP Rename: " + n); }
    public static String renamePromptMsg(UUID id)       { return get(id, "新しいCP名をチャットに入力してください。", "Type a new CP name in chat."); }
    public static String renameCancelWord(UUID id)      { return get(id, "でキャンセル", "to cancel"); }
    public static String renameCancelled(UUID id)       { return get(id, "リネームをキャンセルしました。", "Rename cancelled."); }
    public static String renameSuccess(UUID id, String o, String n) { return get(id, "『" + o + "』を『" + n + "』にリネームしました。", "Renamed '" + o + "' to '" + n + "'."); }
    public static String renameNotFound(UUID id, String n) { return get(id, "CP『" + n + "』が見つかりませんでした。", "CP '" + n + "' not found."); }
    public static String renameExists(UUID id, String n) { return get(id, "CP『" + n + "』は既に存在します。再入力してください。", "CP '" + n + "' already exists. Try again."); }
    public static String renameRetryHint(UUID id)       { return get(id, "  新しいCP名を入力 / 『cancel』でキャンセル", "  Enter new CP name / 'cancel' to cancel"); }

    public static String descPromptTitle(UUID id, String n) { return get(id, "CP 説明の変更: " + n, "CP Description: " + n); }
    public static String descPromptMsg(UUID id)         { return get(id, "説明文をチャットに入力してください。", "Type a description in chat."); }
    public static String descClearWord(UUID id)         { return get(id, "で説明を削除", "to clear description"); }
    public static String descCancelWord(UUID id)        { return get(id, "でキャンセル", "to cancel"); }
    public static String descCancelled(UUID id)         { return get(id, "説明変更をキャンセルしました。", "Description change cancelled."); }
    public static String descRemoved(UUID id, String n) { return get(id, "『" + n + "』の説明を削除しました。", "Description removed from '" + n + "'."); }
    public static String descSet(UUID id, String n)     { return get(id, "『" + n + "』の説明を設定しました。", "Description set for '" + n + "'."); }
    public static String descNotFound(UUID id, String n) { return get(id, "CP『" + n + "』が見つかりませんでした。", "CP '" + n + "' not found."); }

    // Player search
    public static String playerSearchTitle(UUID id)   { return get(id, "プレイヤー検索", "Player Search"); }
    public static String playerSearchMsg(UUID id)     { return get(id, "検索したいプレイヤー名をチャットに入力してください。", "Type a player name in chat to search."); }
    public static String playerSearchName(UUID id)    { return get(id, "プレイヤー名を検索", "Search Player Name"); }

    // -----------------------------------------------------------------------
    // Player select menu
    // -----------------------------------------------------------------------

    public static String psUnknown(UUID id)           { return get(id, "不明", "Unknown"); }
    public static String psCpCount(UUID id)           { return get(id, "CP数: ", "CPs: "); }
    public static String psLastClone(UUID id)         { return get(id, "最終クローン: ", "Last clone: "); }
    public static String psClonedCount(UUID id)       { return get(id, "被クローン回数: ", "Clone count: "); }
    public static String psNearestDist(UUID id)       { return get(id, "最寄り距離: ", "Nearest dist: "); }
    public static String psBlocks(UUID id)            { return get(id, " ブロック", " blocks"); }
    public static String psLastActivity(UUID id)      { return get(id, "最終操作: ", "Last activity: "); }
    public static String psNone(UUID id)              { return get(id, "なし", "None"); }
    public static String psCurrentlyViewing(UUID id)  { return get(id, "✔ 現在表示中", "✔ Currently viewing"); }
    public static String psClickToView(UUID id)       { return get(id, "クリックでCP一覧を表示", "Click to view CPs"); }
    public static String psPlayerCount(UUID id)       { return get(id, "プレイヤー数: ", "Players: "); }

    // -----------------------------------------------------------------------
    // Gamemode toggle
    // -----------------------------------------------------------------------

    public static String gmCreative(UUID id) { return get(id, "ゲームモードをクリエイティブに変更しました。", "Gamemode changed to Creative."); }
    public static String gmAdventure(UUID id) { return get(id, "ゲームモードをアドベンチャーに変更しました。", "Gamemode changed to Adventure."); }

    // -----------------------------------------------------------------------
    // Sort order labels
    // -----------------------------------------------------------------------

    // CP sort orders
    public static String sortNameAsc(UUID id)      { return get(id, "名前順（昇順）", "Name (A→Z)"); }
    public static String sortNameDesc(UUID id)     { return get(id, "名前順（降順）", "Name (Z→A)"); }
    public static String sortCreatedAsc(UUID id)   { return get(id, "作成日時（古い順）", "Created (Oldest)"); }
    public static String sortCreatedDesc(UUID id)  { return get(id, "作成日時（新しい順）", "Created (Newest)"); }
    public static String sortUpdatedAsc(UUID id)   { return get(id, "更新日時（古い順）", "Updated (Oldest)"); }
    public static String sortUpdatedDesc(UUID id)  { return get(id, "更新日時（新しい順）", "Updated (Newest)"); }
    public static String sortDistanceAsc(UUID id)  { return get(id, "距離（近い順）", "Distance (Nearest)"); }

    // Player sort orders
    public static String pSortNameAsc(UUID id)         { return get(id, "名前順（昇順）", "Name (A→Z)"); }
    public static String pSortNameDesc(UUID id)        { return get(id, "名前順（降順）", "Name (Z→A)"); }
    public static String pSortClonedByMe(UUID id)      { return get(id, "クローンした順", "By My Clones"); }
    public static String pSortClonedCount(UUID id)     { return get(id, "被クローン数順", "By Clone Count"); }
    public static String pSortDistanceAsc(UUID id)     { return get(id, "距離（近い順）", "Distance (Nearest)"); }
    public static String pSortActivityDesc(UUID id)    { return get(id, "最終操作（新しい順）", "Last Activity (Newest)"); }
    public static String pSortActivityAsc(UUID id)     { return get(id, "最終操作（古い順）", "Last Activity (Oldest)"); }

    /** Get localized label for a CP SortOrder. */
    public static String sortOrderLabel(UUID id, checkpoint.model.SortOrder order) {
        return switch (order) {
            case NAME_ASC      -> sortNameAsc(id);
            case NAME_DESC     -> sortNameDesc(id);
            case CREATED_ASC   -> sortCreatedAsc(id);
            case CREATED_DESC  -> sortCreatedDesc(id);
            case UPDATED_ASC   -> sortUpdatedAsc(id);
            case UPDATED_DESC  -> sortUpdatedDesc(id);
            case DISTANCE_ASC  -> sortDistanceAsc(id);
        };
    }

    /** Get localized label for a PlayerSortOrder. */
    public static String playerSortOrderLabel(UUID id, checkpoint.model.PlayerSortOrder order) {
        return switch (order) {
            case NAME_ASC           -> pSortNameAsc(id);
            case NAME_DESC          -> pSortNameDesc(id);
            case CLONED_BY_ME_DESC  -> pSortClonedByMe(id);
            case CLONED_COUNT_DESC  -> pSortClonedCount(id);
            case DISTANCE_ASC       -> pSortDistanceAsc(id);
            case LAST_ACTIVITY_DESC -> pSortActivityDesc(id);
            case LAST_ACTIVITY_ASC  -> pSortActivityAsc(id);
        };
    }
}
