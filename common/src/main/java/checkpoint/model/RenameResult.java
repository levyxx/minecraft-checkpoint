package checkpoint.model;

/**
 * Result of a checkpoint rename operation.
 */
public enum RenameResult {
    SUCCESS,
    OLD_NOT_FOUND,
    NEW_ALREADY_EXISTS
}
