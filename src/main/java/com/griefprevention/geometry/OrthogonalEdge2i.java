package com.griefprevention.geometry;

import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * An axis-aligned edge between two points in the X/Z plane.
 */
public record OrthogonalEdge2i(@NotNull OrthogonalPoint2i start, @NotNull OrthogonalPoint2i end)
{
    public boolean isHorizontal()
    {
        return start.z() == end.z() && start.x() != end.x();
    }

    public boolean isVertical()
    {
        return start.x() == end.x() && start.z() != end.z();
    }

    public boolean isOrthogonal()
    {
        return isHorizontal() || isVertical();
    }

    public int length()
    {
        return Math.abs(end.x() - start.x()) + Math.abs(end.z() - start.z());
    }

    public int minX()
    {
        return Math.min(start.x(), end.x());
    }

    public int maxX()
    {
        return Math.max(start.x(), end.x());
    }

    public int minZ()
    {
        return Math.min(start.z(), end.z());
    }

    public int maxZ()
    {
        return Math.max(start.z(), end.z());
    }

    public boolean containsPoint(@NotNull OrthogonalPoint2i point)
    {
        if (isHorizontal())
        {
            return point.z() == start.z() && point.x() >= minX() && point.x() <= maxX();
        }

        if (isVertical())
        {
            return point.x() == start.x() && point.z() >= minZ() && point.z() <= maxZ();
        }

        return false;
    }

    public boolean containsInteriorPoint(@NotNull OrthogonalPoint2i point)
    {
        return containsPoint(point) && !point.equals(start) && !point.equals(end);
    }

    public @NotNull BlockFace outwardFaceForPositiveOffset()
    {
        if (isHorizontal())
        {
            return BlockFace.SOUTH;
        }

        if (isVertical())
        {
            return BlockFace.EAST;
        }

        throw new IllegalStateException("Non-orthogonal edges do not have a normal.");
    }
}
