package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single validation issue found in a proposed orthogonal polygon.
 */
public record OrthogonalPolygonValidationIssue(
        @NotNull OrthogonalPolygonValidationIssueType type,
        @NotNull String message,
        @Nullable OrthogonalPoint2i point,
        @Nullable Integer firstEdgeIndex,
        @Nullable Integer secondEdgeIndex
)
{
}
