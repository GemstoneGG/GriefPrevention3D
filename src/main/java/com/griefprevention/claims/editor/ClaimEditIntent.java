package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A source-agnostic request against the claim editor.
 */
public record ClaimEditIntent(
        @NotNull ClaimEditIntentType type,
        @NotNull ClaimEditSource source,
        @Nullable ClaimEditorMode mode,
        @Nullable Long claimId,
        @Nullable OrthogonalPoint2i point,
        @Nullable Integer amount,
        boolean holdingModificationTool,
        @NotNull List<OrthogonalPoint2i> selectedCells
)
{
    public ClaimEditIntent
    {
        selectedCells = List.copyOf(selectedCells);
    }

    public static @NotNull ClaimEditIntent enterMode(@NotNull ClaimEditSource source, @NotNull ClaimEditorMode mode)
    {
        return new ClaimEditIntent(ClaimEditIntentType.ENTER_MODE, source, mode, null, null, null, false, List.of());
    }

    public static @NotNull ClaimEditIntent exitMode(@NotNull ClaimEditSource source)
    {
        return new ClaimEditIntent(ClaimEditIntentType.EXIT_MODE, source, null, null, null, null, false, List.of());
    }
}
