package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A non-committed result to visualize or describe back to the player.
 */
public record ClaimEditPreview(
        @Nullable OrthogonalPolygon polygon,
        @Nullable SegmentSelection highlightedSegment,
        @NotNull List<OrthogonalPoint2i> draftPoints,
        @Nullable OrthogonalPoint2i snappedPoint,
        @NotNull List<OrthogonalPoint2i> conflictPoints,
        @NotNull List<OrthogonalPolygonValidationIssue> issues,
        @NotNull List<String> messages
)
{
    public ClaimEditPreview
    {
        draftPoints = List.copyOf(draftPoints);
        conflictPoints = List.copyOf(conflictPoints);
        issues = List.copyOf(issues);
        messages = List.copyOf(messages);
    }

    public static @NotNull ClaimEditPreview empty()
    {
        return new ClaimEditPreview(null, null, List.of(), null, List.of(), List.of(), List.of());
    }
}
