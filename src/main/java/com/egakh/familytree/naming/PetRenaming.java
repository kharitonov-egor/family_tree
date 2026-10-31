package com.egakh.familytree.naming;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.PetAccess;

import java.util.UUID;

public final class PetRenaming {
    public static final int MAX_NAME_LENGTH = 64;

    /** Sent as its ordinal in {@code rename_result_v3}, so new values go at the end. */
    public enum Result {
        SUCCESS, NOT_FOUND, NOT_ALLOWED, INVALID_NAME, READ_ONLY, BUSY, NO_PERMISSION;

        /**
         * The value to send a client. NO_PERMISSION, for a denied {@code familytree.rename}, arrived with 1.4.0;
         * older clients would call it a failed save, so they get NOT_ALLOWED as 1.3.0 sent.
         */
        public int wireStatus(boolean clientKnowsNoPermission) {
            return this == NO_PERMISSION && !clientKnowsNoPermission ? NOT_ALLOWED.ordinal() : ordinal();
        }
    }

    private PetRenaming() {}

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH
                && name.codePoints().allMatch(c -> !Character.isISOControl(c)
                && c != 0x00A7 && (c < 0xD800 || c > 0xDFFF));
    }

    /** An empty name clears the override. Viewing another player's pets does not grant edit access. */
    public static Result rename(AnimalRecord record, UUID playerId, boolean operator, String name) {
        if (record == null) return Result.NOT_FOUND;
        if (!PetAccess.canManage(record, playerId, operator)) return Result.NOT_ALLOWED;
        if (name == null || (!name.isEmpty() && !isValidName(name))) return Result.INVALID_NAME;
        record.setTreeName(name.isEmpty() ? null : name.strip());
        return Result.SUCCESS;
    }
}
