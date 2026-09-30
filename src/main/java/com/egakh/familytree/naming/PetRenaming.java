package com.egakh.familytree.naming;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.PetAccess;

import java.util.UUID;

public final class PetRenaming {
    public static final int MAX_NAME_LENGTH = 64;

    public enum Result {
        SUCCESS, NOT_FOUND, NOT_ALLOWED, INVALID_NAME, READ_ONLY, BUSY
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
