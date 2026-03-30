package com.griefprevention.claims.editor;

import org.jetbrains.annotations.Nullable;

/**
 * Identifies the current claim editing target.
 */
public record ClaimEditTarget(
        ClaimEditTargetType type,
        @Nullable Long claimId
)
{
}
