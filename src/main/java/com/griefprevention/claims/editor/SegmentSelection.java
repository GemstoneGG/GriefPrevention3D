package com.griefprevention.claims.editor;

import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.Nullable;

/**
 * An editable subsection of a parent-claim boundary.
 */
public record SegmentSelection(
        long claimId,
        int edgeIndex,
        @Nullable Integer startNodeIndex,
        @Nullable Integer endNodeIndex,
        @Nullable BlockFace outwardFace
)
{
}
