package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssue;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssueType;
import com.griefprevention.geometry.OrthogonalPolygonValidationResult;
import me.ryanhamshire.GriefPrevention.Messages;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal placeholder implementation to anchor the recode editor boundary.
 */
public final class ClaimEditorSkeleton implements ClaimEditor
{
    @Override
    public @NotNull ClaimEditResult apply(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        return switch (intent.type())
        {
            case ENTER_MODE -> ClaimEditResult.success(
                    session.withMode(intent.mode(), intent.source()).withPreview(ClaimEditPreview.empty()),
                    ClaimEditPreview.empty(),
                    List.of("Entered " + intent.mode() + " mode.")
            );
            case EXIT_MODE -> ClaimEditResult.success(
                    ClaimEditorSession.idle(session.playerId()),
                    ClaimEditPreview.empty(),
                    List.of("Exited claim editor mode.")
            );
            case SELECT_SEGMENT -> handleSelectSegment(session, intent);
            case ADD_CORNER -> handleAddCorner(session, intent);
            case ADD_NODE -> handleAddNode(session, intent);
            case EXPAND_SEGMENT -> handleExpandSegment(session, intent);
            case CANCEL_PATH -> handleCancelPath(session);
            default -> ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("Claim editor intent not implemented yet: " + intent.type())
            );
        };
    }

    private @NotNull ClaimEditResult handleSelectSegment(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("Segments can only be selected while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    Messages.MustHoldModificationToolForThat,
                    session,
                    session.preview(),
                    List.of("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("No parent claim boundary is selected for segment editing.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("No segment selection point was provided.")
            );
        }

        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.size() != 1)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("That point does not resolve to a single editable segment.")
            );
        }

        int edgeIndex = matches.getFirst();
        SegmentSelection selection = new SegmentSelection(resolveClaimId(session), edgeIndex, null, null, null);
        ClaimEditPreview preview = new ClaimEditPreview(
                polygon,
                selection,
                session.preview().draftPoints(),
                session.preview().snappedPoint(),
                session.preview().conflictPoints(),
                session.preview().issues(),
                List.of("Segment selected.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleExpandSegment(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("Segments can only be expanded while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    Messages.MustHoldModificationToolForThat,
                    session,
                    session.preview(),
                    List.of("You must be holding a golden shovel to do that.")
            );
        }

        SegmentSelection activeSegment = session.activeSegment();
        if (activeSegment == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NO_ACTIVE_SEGMENT,
                    null,
                    session,
                    session.preview(),
                    List.of("Select a claim segment before expanding it.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("No parent claim boundary is selected for segment editing.")
            );
        }

        int amount = intent.amount() == null ? 0 : intent.amount();
        OrthogonalPolygonValidationResult validationResult;
        SegmentSelection updatedSelection;
        try
        {
            SegmentExpansion expansion = expandSegment(polygon, activeSegment, amount);
            validationResult = expansion.result();
            updatedSelection = expansion.selection();
        }
        catch (IllegalArgumentException exception)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of(exception.getMessage())
            );
        }

        if (!validationResult.isValid())
        {
            ClaimEditPreview preview = previewFromPolygonValidation(
                    validationResult,
                    List.of("That segment cannot be expanded there.")
            );
            ClaimEditFailureType failureType = validationResult.issues().stream()
                    .anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION)
                    ? ClaimEditFailureType.SELF_INTERSECTION
                    : ClaimEditFailureType.INVALID_GEOMETRY;
            return ClaimEditResult.failure(failureType, null, session.withPreview(preview), preview, preview.messages());
        }

        OrthogonalPolygon updatedPolygon = validationResult.polygon();
        ClaimEditPreview preview = new ClaimEditPreview(
                updatedPolygon,
                updatedSelection,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of("Segment expanded.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(updatedSelection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleAddCorner(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("Corners can only be added while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    Messages.MustHoldModificationToolForThat,
                    session,
                    session.preview(),
                    List.of("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("No shaped corner point was provided.")
            );
        }

        ShapedPathDraft draft = session.openPath();
        if (draft == null)
        {
            draft = ShapedPathDraft.empty(intent.claimId());
        }

        OrthogonalPolygon basePolygon = session.activeTarget() != null
                && session.activeTarget().type() == ClaimEditTargetType.EXISTING_PARENT_CLAIM
                ? session.preview().polygon()
                : null;

        List<OrthogonalPoint2i> points = draft.points();
        if (points.isEmpty())
        {
            if (basePolygon != null && !isBoundaryPoint(basePolygon, point))
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                        null,
                        session,
                        session.preview(),
                        List.of("Start reshaping by clicking an existing claim boundary point.")
                );
            }

            ShapedPathDraft updatedDraft = draft.withAddedPoint(point);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    List.of(basePolygon == null ? "First shaped corner set." : "Segment point added.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        OrthogonalPoint2i lastPoint = points.getLast();
        OrthogonalPoint2i firstPoint = points.getFirst();
        OrthogonalPoint2i rawPoint = point;

        if (point.equals(lastPoint))
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    session.preview(),
                    List.of("That corner is already the current endpoint.")
            );
        }

        boolean closesPath = point.equals(firstPoint);
        if (!isOrthogonalStep(lastPoint, point))
        {
            point = snapToOrthogonal(lastPoint, point);
            closesPath = point.equals(firstPoint);

            if (point.equals(lastPoint))
            {
                ShapedPathDraft snappedDraft = draft.withSnappedPreview(point);
                ClaimEditPreview preview = draftPreview(snappedDraft, basePolygon, List.of("Shaped claims must turn on the same X or Z axis."));
                return ClaimEditResult.failure(
                        ClaimEditFailureType.INVALID_GEOMETRY,
                        null,
                        session.withOpenPath(snappedDraft).withPreview(preview),
                        preview,
                        preview.messages()
                );
            }
        }

        if (!closesPath && points.contains(point))
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    draftPreview(draft, basePolygon, List.of("That corner is already part of the current shaped path.")),
                    List.of("That corner is already part of the current shaped path.")
            );
        }

        if (closesPath && points.size() < 4)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    draftPreview(draft, basePolygon, List.of("A shaped claim needs at least 4 corners before it can close.")),
                    List.of("A shaped claim needs at least 4 corners before it can close.")
            );
        }

        List<OrthogonalPoint2i> candidatePoints = new ArrayList<>(points);
        OrthogonalPoint2i mergeBoundaryPoint = null;
        if (basePolygon != null)
        {
            mergeBoundaryPoint = findBoundaryReconnectPoint(
                    basePolygon,
                    lastPoint,
                    point,
                    point.equals(firstPoint) ? firstPoint : null
            );
        }

        OrthogonalPoint2i appliedPoint = mergeBoundaryPoint == null ? point : mergeBoundaryPoint;
        candidatePoints.add(appliedPoint);

        boolean reshapeLeavesBoundary = basePolygon != null && reshapeLeavesBoundary(basePolygon, candidatePoints);

        if (basePolygon != null && mergeBoundaryPoint != null && reshapeLeavesBoundary)
        {
            OrthogonalPolygonValidationResult mergeResult;
            try
            {
                mergeResult = mergeDraftIntoExistingPolygon(basePolygon, candidatePoints);
            }
            catch (IllegalArgumentException exception)
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.INVALID_GEOMETRY,
                        null,
                        session,
                        session.preview(),
                        List.of(exception.getMessage())
                );
            }
            if (!mergeResult.isValid())
            {
                ClaimEditPreview preview = previewFromValidation(
                        new ShapedPathDraft(draft.claimId(), candidatePoints, null, false),
                        basePolygon,
                        mergeResult,
                        List.of("That reshape path cannot merge cleanly into the existing claim boundary.")
                );
                ClaimEditFailureType failureType = mergeResult.issues().stream()
                        .anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION)
                        ? ClaimEditFailureType.SELF_INTERSECTION
                        : ClaimEditFailureType.INVALID_GEOMETRY;
                return ClaimEditResult.failure(failureType, null, session.withPreview(preview), preview, preview.messages());
            }

            ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, true);
            ClaimEditPreview preview = closedPreview(
                    updatedDraft,
                    mergeResult.polygon(),
                    List.of("Boundary reshape path merged.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        if (basePolygon != null && mergeBoundaryPoint != null)
        {
            // If clicking on another boundary point that extends the segment along the boundary,
            // add it as a proper corner to allow creating multi-segment reshape paths.
            // This enables: 1) creating 2+ segments for /expandclaim, or
            // 2) closing a reshape merge path that goes outside and back to a different segment.
            ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, false);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    List.of("Segment point added.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        if (!closesPath)
        {
            ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, false);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    rawPoint.equals(point)
                            ? List.of("Shaped corner added.")
                            : List.of("Shaped corner snapped into orthogonal alignment.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        OrthogonalPolygonValidationResult validationResult = OrthogonalPolygon.validatePath(candidatePoints);
        if (!validationResult.isValid())
        {
            ClaimEditPreview preview = previewFromValidation(draft, basePolygon, validationResult, List.of("This shaped claim would intersect itself or violate closure rules."));
            ClaimEditFailureType failureType = validationResult.issues().stream()
                    .anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION)
                    ? ClaimEditFailureType.SELF_INTERSECTION
                    : ClaimEditFailureType.INVALID_GEOMETRY;
            return ClaimEditResult.failure(failureType, null, session.withPreview(preview), preview, preview.messages());
        }

        ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, true);
        ClaimEditPreview preview = closedPreview(
                updatedDraft,
                validationResult.polygon(),
                rawPoint.equals(point) ? List.of("Shaped path closed.") : List.of("Shaped path snapped closed.")
        );
        return ClaimEditResult.success(
                session.withOpenPath(updatedDraft).withPreview(preview),
                preview,
                preview.messages()
        );
    }

    private @Nullable OrthogonalPoint2i findBoundaryReconnectPoint(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i from,
            @NotNull OrthogonalPoint2i to,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        OrthogonalEdge2i segment = new OrthogonalEdge2i(from, to);
        if (!segment.isOrthogonal())
        {
            return null;
        }

        OrthogonalPoint2i bestPoint = null;
        int bestDistance = Integer.MAX_VALUE;
        for (OrthogonalEdge2i edge : polygon.edges())
        {
            OrthogonalPoint2i candidate = nearestIntersectionPoint(segment, edge, excludedPoint);
            if (candidate == null || candidate.equals(from))
            {
                continue;
            }

            int distance = Math.abs(candidate.x() - from.x()) + Math.abs(candidate.z() - from.z());
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestPoint = candidate;
            }
        }

        return bestPoint;
    }

    private @Nullable OrthogonalPoint2i nearestIntersectionPoint(
            @NotNull OrthogonalEdge2i segment,
            @NotNull OrthogonalEdge2i boundary,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        if (segment.isHorizontal() && boundary.isVertical())
        {
            OrthogonalPoint2i point = new OrthogonalPoint2i(boundary.start().x(), segment.start().z());
            return segment.containsPoint(point) && boundary.containsPoint(point) && !point.equals(excludedPoint) ? point : null;
        }

        if (segment.isVertical() && boundary.isHorizontal())
        {
            OrthogonalPoint2i point = new OrthogonalPoint2i(segment.start().x(), boundary.start().z());
            return segment.containsPoint(point) && boundary.containsPoint(point) && !point.equals(excludedPoint) ? point : null;
        }

        if (segment.isHorizontal() && boundary.isHorizontal() && segment.start().z() == boundary.start().z())
        {
            return nearestOverlapPoint(
                    segment.start(),
                    segment.end(),
                    Math.max(segment.minX(), boundary.minX()),
                    Math.min(segment.maxX(), boundary.maxX()),
                    true,
                    excludedPoint
            );
        }

        if (segment.isVertical() && boundary.isVertical() && segment.start().x() == boundary.start().x())
        {
            return nearestOverlapPoint(
                    segment.start(),
                    segment.end(),
                    Math.max(segment.minZ(), boundary.minZ()),
                    Math.min(segment.maxZ(), boundary.maxZ()),
                    false,
                    excludedPoint
            );
        }

        return null;
    }

    private @Nullable OrthogonalPoint2i nearestOverlapPoint(
            @NotNull OrthogonalPoint2i from,
            @NotNull OrthogonalPoint2i to,
            int overlapMin,
            int overlapMax,
            boolean horizontal,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        if (overlapMin > overlapMax)
        {
            return null;
        }

        int step = horizontal
                ? Integer.compare(to.x(), from.x())
                : Integer.compare(to.z(), from.z());
        if (step == 0)
        {
            return null;
        }

        int coordinate = horizontal ? from.x() + step : from.z() + step;
        while (coordinate >= overlapMin && coordinate <= overlapMax)
        {
            OrthogonalPoint2i point = horizontal
                    ? new OrthogonalPoint2i(coordinate, from.z())
                    : new OrthogonalPoint2i(from.x(), coordinate);
            if (!point.equals(excludedPoint))
            {
                return point;
            }
            coordinate += step;
        }

        return null;
    }

    private boolean isOrthogonalStep(@NotNull OrthogonalPoint2i first, @NotNull OrthogonalPoint2i second)
    {
        return first.x() == second.x() ^ first.z() == second.z();
    }

    private @NotNull OrthogonalPoint2i snapToOrthogonal(@NotNull OrthogonalPoint2i anchor, @NotNull OrthogonalPoint2i rawPoint)
    {
        int dX = Math.abs(rawPoint.x() - anchor.x());
        int dZ = Math.abs(rawPoint.z() - anchor.z());
        if (dX < dZ)
        {
            return new OrthogonalPoint2i(anchor.x(), rawPoint.z());
        }

        return new OrthogonalPoint2i(rawPoint.x(), anchor.z());
    }

    private boolean reshapeLeavesBoundary(
            @NotNull OrthogonalPolygon polygon,
            @NotNull List<OrthogonalPoint2i> draftPoints)
    {
        if (draftPoints.size() < 3)
        {
            return false;
        }

        for (int i = 1; i < draftPoints.size() - 1; i++)
        {
            if (!isBoundaryPoint(polygon, draftPoints.get(i)))
            {
                return true;
            }
        }

        return false;
    }

    private @NotNull ClaimEditPreview draftPreview(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull List<String> messages)
    {
        return new ClaimEditPreview(polygon, null, draft.points(), draft.snappedPreviewPoint(), List.of(), List.of(), messages);
    }

    private @NotNull ClaimEditPreview closedPreview(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditPreview(polygon, null, draft.points(), draft.snappedPreviewPoint(), List.of(), List.of(), messages);
    }

    private @NotNull ClaimEditPreview previewFromValidation(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull OrthogonalPolygonValidationResult validationResult,
            @NotNull List<String> messages
    )
    {
        List<OrthogonalPoint2i> conflictPoints = validationResult.issues().stream()
                .map(OrthogonalPolygonValidationIssue::point)
                .filter(point -> point != null)
                .toList();
        return new ClaimEditPreview(
                validationResult.polygon() == null ? polygon : validationResult.polygon(),
                null,
                draft.points(),
                draft.snappedPreviewPoint(),
                conflictPoints,
                validationResult.issues(),
                messages
        );
    }

    private @NotNull ClaimEditPreview previewFromPolygonValidation(
            @NotNull OrthogonalPolygonValidationResult validationResult,
            @NotNull List<String> messages
    )
    {
        List<OrthogonalPoint2i> conflictPoints = validationResult.issues().stream()
                .map(OrthogonalPolygonValidationIssue::point)
                .filter(point -> point != null)
                .toList();
        return new ClaimEditPreview(
                validationResult.polygon(),
                null,
                List.of(),
                null,
                conflictPoints,
                validationResult.issues(),
                messages
        );
    }

    private boolean isBoundaryPoint(@NotNull OrthogonalPolygon polygon, @NotNull OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point))
        {
            return true;
        }

        return polygon.edgeIndexesContainingInteriorPoint(point).size() == 1;
    }

    private @NotNull OrthogonalPolygonValidationResult mergeDraftIntoExistingPolygon(
            @NotNull OrthogonalPolygon polygon,
            @NotNull List<OrthogonalPoint2i> draftPoints)
    {
        OrthogonalPoint2i start = draftPoints.getFirst();
        OrthogonalPoint2i end = draftPoints.getLast();

        OrthogonalPolygon working = ensureBoundaryVertex(ensureBoundaryVertex(polygon, start), end);
        int startIndex = working.corners().indexOf(start);
        int endIndex = working.corners().indexOf(end);
        if (startIndex < 0 || endIndex < 0 || startIndex == endIndex)
        {
            throw new IllegalArgumentException("Reshape paths must begin and end on different boundary points.");
        }

        OrthogonalPolygonValidationResult forwardCandidate = validateMergedCandidate(
                draftPoints,
                pathAlongPolygon(working.corners(), endIndex, startIndex, true)
        );
        OrthogonalPolygonValidationResult reverseCandidate = validateMergedCandidate(
                draftPoints,
                pathAlongPolygon(working.corners(), endIndex, startIndex, false)
        );

        OrthogonalPolygonValidationResult unionFallback = tryUnionFallback(working, draftPoints, forwardCandidate, reverseCandidate);
        if (unionFallback != null)
        {
            return unionFallback;
        }

        return chooseMergedCandidate(working, draftPoints, forwardCandidate, reverseCandidate);
    }

    private @NotNull OrthogonalPolygon ensureBoundaryVertex(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point))
        {
            return polygon;
        }

        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.size() != 1)
        {
            throw new IllegalArgumentException("Point does not lie on the existing claim boundary.");
        }

        return polygon.insertNode(matches.getFirst(), point);
    }

    private @NotNull List<OrthogonalPoint2i> pathAlongPolygon(
            @NotNull List<OrthogonalPoint2i> corners,
            int fromIndex,
            int toIndex,
            boolean forward)
    {
        List<OrthogonalPoint2i> path = new ArrayList<>();
        int index = fromIndex;
        path.add(corners.get(index));
        while (index != toIndex)
        {
            index = forward
                    ? (index + 1) % corners.size()
                    : (index - 1 + corners.size()) % corners.size();
            path.add(corners.get(index));
        }
        return path;
    }

    private @NotNull OrthogonalPolygonValidationResult validateMergedCandidate(
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @NotNull List<OrthogonalPoint2i> boundaryArc)
    {
        List<OrthogonalPoint2i> candidatePath = new ArrayList<>(draftPoints);
        candidatePath.addAll(boundaryArc.subList(1, boundaryArc.size()));
        return OrthogonalPolygon.validatePath(candidatePath);
    }

    private @NotNull OrthogonalPolygonValidationResult chooseMergedCandidate(
            @NotNull OrthogonalPolygon originalPolygon,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @Nullable OrthogonalPolygonValidationResult first,
            @Nullable OrthogonalPolygonValidationResult second)
    {
        boolean firstValid = first != null && first.isValid() && first.polygon() != null;
        boolean secondValid = second != null && second.isValid() && second.polygon() != null;

        if (firstValid && !secondValid)
        {
            return first;
        }

        if (!firstValid && secondValid)
        {
            return second;
        }

        if (!firstValid)
        {
            throw new IllegalArgumentException("That reshape path cannot merge cleanly into the existing claim boundary.");
        }

        boolean firstContainsOriginal = polygonContainsPolygon(first.polygon(), originalPolygon);
        boolean secondContainsOriginal = polygonContainsPolygon(second.polygon(), originalPolygon);
        if (firstContainsOriginal != secondContainsOriginal)
        {
            return firstContainsOriginal ? first : second;
        }

        boolean extendsOutsideOriginal = draftPoints.stream()
                .skip(1)
                .limit(Math.max(0, draftPoints.size() - 2L))
                .anyMatch(point -> !polygonContains(originalPolygon, point));

        int firstArea = polygonArea(first.polygon());
        int secondArea = polygonArea(second.polygon());
        if (firstArea != secondArea)
        {
            if (extendsOutsideOriginal)
            {
                return firstArea > secondArea ? first : second;
            }

            return firstArea < secondArea ? first : second;
        }

        int firstOverlap = polygonOverlapArea(originalPolygon, first.polygon());
        int secondOverlap = polygonOverlapArea(originalPolygon, second.polygon());
        if (firstOverlap != secondOverlap)
        {
            return firstOverlap > secondOverlap ? first : second;
        }

        return first;
    }

    private @Nullable OrthogonalPolygonValidationResult tryUnionFallback(
            @NotNull OrthogonalPolygon originalPolygon,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @NotNull OrthogonalPolygonValidationResult first,
            @NotNull OrthogonalPolygonValidationResult second)
    {
        boolean extendsOutsideOriginal = draftPoints.stream()
                .skip(1)
                .limit(Math.max(0, draftPoints.size() - 2L))
                .anyMatch(point -> !polygonContains(originalPolygon, point));

        if (!extendsOutsideOriginal)
        {
            return null;
        }

        OrthogonalPolygon patch = null;
        if (first.isValid() && first.polygon() != null && !polygonContainsPolygon(first.polygon(), originalPolygon))
        {
            patch = first.polygon();
        }
        else if (second.isValid() && second.polygon() != null && !polygonContainsPolygon(second.polygon(), originalPolygon))
        {
            patch = second.polygon();
        }

        if (patch == null)
        {
            return null;
        }

        try
        {
            OrthogonalPolygon unionPolygon = unionPolygons(originalPolygon, patch);
            OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(unionPolygon.closedPath());
            return result.isValid() ? result : null;
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }

    private boolean polygonContains(@NotNull OrthogonalPolygon polygon, @NotNull OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point))
        {
            return true;
        }

        if (!polygon.edgeIndexesContainingInteriorPoint(point).isEmpty())
        {
            return true;
        }

        double sampleX = point.x() + 0.5D;
        double sampleZ = point.z() + 0.5D;
        boolean inside = false;
        List<OrthogonalPoint2i> corners = polygon.corners();
        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++)
        {
            OrthogonalPoint2i a = corners.get(i);
            OrthogonalPoint2i b = corners.get(j);
            boolean crosses = (a.z() > sampleZ) != (b.z() > sampleZ);
            if (!crosses)
            {
                continue;
            }

            double intersectionX = (double) (b.x() - a.x()) * (sampleZ - a.z()) / (double) (b.z() - a.z()) + a.x();
            if (sampleX < intersectionX)
            {
                inside = !inside;
            }
        }

        return inside;
    }

    private int polygonArea(@NotNull OrthogonalPolygon polygon)
    {
        int area = 0;
        for (int x = polygon.minX(); x <= polygon.maxX(); x++)
        {
            for (int z = polygon.minZ(); z <= polygon.maxZ(); z++)
            {
                if (polygonContains(polygon, new OrthogonalPoint2i(x, z)))
                {
                    area++;
                }
            }
        }

        return area;
    }

    private boolean polygonContainsPolygon(@NotNull OrthogonalPolygon container, @NotNull OrthogonalPolygon contents)
    {
        for (int x = contents.minX(); x <= contents.maxX(); x++)
        {
            for (int z = contents.minZ(); z <= contents.maxZ(); z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (polygonContains(contents, point) && !polygonContains(container, point))
                {
                    return false;
                }
            }
        }

        return true;
    }

    private int polygonOverlapArea(@NotNull OrthogonalPolygon first, @NotNull OrthogonalPolygon second)
    {
        int area = 0;
        int minX = Math.max(first.minX(), second.minX());
        int maxX = Math.min(first.maxX(), second.maxX());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxZ = Math.min(first.maxZ(), second.maxZ());

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (polygonContains(first, point) && polygonContains(second, point))
                {
                    area++;
                }
            }
        }

        return area;
    }

    private @NotNull OrthogonalPolygon unionPolygons(
            @NotNull OrthogonalPolygon first,
            @NotNull OrthogonalPolygon second)
    {
        Set<OrthogonalPoint2i> occupied = new HashSet<>();
        int minX = Math.min(first.minX(), second.minX());
        int maxX = Math.max(first.maxX(), second.maxX());
        int minZ = Math.min(first.minZ(), second.minZ());
        int maxZ = Math.max(first.maxZ(), second.maxZ());

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (polygonContains(first, point) || polygonContains(second, point))
                {
                    occupied.add(point);
                }
            }
        }

        if (occupied.isEmpty())
        {
            throw new IllegalArgumentException("That reshape path would remove the claim body.");
        }

        return polygonFromOccupiedPoints(occupied);
    }

    private @NotNull OrthogonalPolygon polygonFromOccupiedPoints(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        Set<OrthogonalPoint2i> boundary = occupied.stream()
                .filter(point -> isBoundaryOccupiedPoint(occupied, point))
                .collect(HashSet::new, Set::add, Set::addAll);
        debug("polygonFromOccupiedPoints occupied=%s boundary=%s", occupied, boundary);
        if (boundary.isEmpty())
        {
            throw new IllegalArgumentException("Unable to trace merged claim boundary.");
        }

        Map<OrthogonalPoint2i, List<OrthogonalPoint2i>> neighbors = new HashMap<>();
        for (OrthogonalPoint2i point : boundary)
        {
            List<OrthogonalPoint2i> adjacent = new ArrayList<>(4);
            addBoundaryNeighbor(boundary, point, 1, 0, adjacent);
            addBoundaryNeighbor(boundary, point, 0, 1, adjacent);
            addBoundaryNeighbor(boundary, point, -1, 0, adjacent);
            addBoundaryNeighbor(boundary, point, 0, -1, adjacent);
            neighbors.put(point, adjacent);
        }

        OrthogonalPoint2i start = boundary.stream()
                .filter(point -> isCornerPoint(neighbors.get(point)))
                .min(Comparator.<OrthogonalPoint2i>comparingInt(OrthogonalPoint2i::z)
                        .thenComparingInt(OrthogonalPoint2i::x))
                .orElseGet(() -> boundary.stream()
                        .min(Comparator.<OrthogonalPoint2i>comparingInt(OrthogonalPoint2i::z)
                                .thenComparingInt(OrthogonalPoint2i::x))
                        .orElseThrow());

        List<OrthogonalPoint2i> startNeighbors = neighbors.get(start);
        if (startNeighbors == null || startNeighbors.isEmpty())
        {
            throw new IllegalArgumentException("Merged claim boundary is disconnected.");
        }
        debug("polygonFromOccupiedPoints start=%s startNeighbors=%s", start, startNeighbors);

        OrthogonalPoint2i previous = start;
        OrthogonalPoint2i current = startNeighbors.stream()
                .min(Comparator.comparingInt(candidate -> initialDirectionPriority(start, candidate)))
                .orElseThrow();
        List<OrthogonalPoint2i> traced = new ArrayList<>();
        traced.add(start);
        int guard = boundary.size() * 4 + 4;
        while (guard-- > 0)
        {
            traced.add(current);
            if (current.equals(start))
            {
                break;
            }

            List<OrthogonalPoint2i> currentNeighbors = neighbors.get(current);
            OrthogonalPoint2i from = previous;
            OrthogonalPoint2i at = current;
            OrthogonalPoint2i next = currentNeighbors.stream()
                    .filter(candidate -> !candidate.equals(from))
                    .min(Comparator.comparingInt(candidate -> pointTurnPriority(from, at, candidate)))
                    .orElse(null);

            if (next == null)
            {
                debug("polygonFromOccupiedPoints follow-failure previous=%s current=%s currentNeighbors=%s traced=%s",
                        previous,
                        current,
                        currentNeighbors,
                        traced);
                throw new IllegalArgumentException("Merged claim boundary could not be followed.");
            }

            previous = current;
            current = next;
        }

        if (!traced.getLast().equals(start))
        {
            debug("polygonFromOccupiedPoints close-failure traced=%s", traced);
            throw new IllegalArgumentException("Merged claim boundary did not close.");
        }

        List<OrthogonalPoint2i> compressed = compressBoundaryPath(traced);
        debug("polygonFromOccupiedPoints traced=%s compressed=%s", traced, compressed);
        return OrthogonalPolygon.fromClosedPath(compressed);
    }

    private int initialDirectionPriority(@NotNull OrthogonalPoint2i start, @NotNull OrthogonalPoint2i next)
    {
        return switch (directionIndex(start, next))
        {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    private int pointTurnPriority(
            @NotNull OrthogonalPoint2i previous,
            @NotNull OrthogonalPoint2i current,
            @NotNull OrthogonalPoint2i candidate)
    {
        int currentDirection = directionIndex(previous, current);
        int candidateDirection = directionIndex(current, candidate);
        int delta = Math.floorMod(candidateDirection - currentDirection, 4);
        return switch (delta)
        {
            case 1 -> 0;
            case 0 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    private int directionIndex(@NotNull OrthogonalPoint2i start, @NotNull OrthogonalPoint2i end)
    {
        if (end.x() > start.x())
        {
            return 0;
        }
        if (end.z() > start.z())
        {
            return 1;
        }
        if (end.x() < start.x())
        {
            return 2;
        }
        if (end.z() < start.z())
        {
            return 3;
        }
        throw new IllegalArgumentException("Boundary path cannot contain duplicate points.");
    }

    private boolean isBoundaryOccupiedPoint(@NotNull Set<OrthogonalPoint2i> occupied, @NotNull OrthogonalPoint2i point)
    {
        return !occupied.contains(new OrthogonalPoint2i(point.x() + 1, point.z()))
                || !occupied.contains(new OrthogonalPoint2i(point.x() - 1, point.z()))
                || !occupied.contains(new OrthogonalPoint2i(point.x(), point.z() + 1))
                || !occupied.contains(new OrthogonalPoint2i(point.x(), point.z() - 1));
    }

    private void addBoundaryNeighbor(
            @NotNull Set<OrthogonalPoint2i> boundary,
            @NotNull OrthogonalPoint2i point,
            int dx,
            int dz,
            @NotNull List<OrthogonalPoint2i> adjacent)
    {
        OrthogonalPoint2i neighbor = new OrthogonalPoint2i(point.x() + dx, point.z() + dz);
        if (boundary.contains(neighbor))
        {
            adjacent.add(neighbor);
        }
    }

    private boolean isCornerPoint(@Nullable List<OrthogonalPoint2i> neighbors)
    {
        if (neighbors == null || neighbors.size() != 2)
        {
            return false;
        }

        OrthogonalPoint2i first = neighbors.getFirst();
        OrthogonalPoint2i second = neighbors.getLast();
        return first.x() != second.x() && first.z() != second.z();
    }

    private @NotNull List<OrthogonalPoint2i> compressBoundaryPath(@NotNull List<OrthogonalPoint2i> traced)
    {
        List<OrthogonalPoint2i> path = new ArrayList<>(traced);
        if (path.size() < 4)
        {
            throw new IllegalArgumentException("Merged claim boundary is too small.");
        }

        List<OrthogonalPoint2i> compressed = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++)
        {
            OrthogonalPoint2i previous = path.get((i - 1 + path.size() - 1) % (path.size() - 1));
            OrthogonalPoint2i current = path.get(i);
            OrthogonalPoint2i next = path.get((i + 1) % (path.size() - 1));

            int dx1 = Integer.compare(current.x(), previous.x());
            int dz1 = Integer.compare(current.z(), previous.z());
            int dx2 = Integer.compare(next.x(), current.x());
            int dz2 = Integer.compare(next.z(), current.z());

            if (i == 0 || dx1 != dx2 || dz1 != dz2)
            {
                compressed.add(current);
            }
        }

        compressed.add(compressed.getFirst());
        return compressed;
    }

    private @NotNull ClaimEditResult handleAddNode(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("Nodes can only be added while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    Messages.MustHoldModificationToolForThat,
                    session,
                    session.preview(),
                    List.of("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    List.of("No parent claim boundary is selected for node editing.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("No node point was provided.")
            );
        }

        int existingCornerIndex = polygon.corners().indexOf(point);
        if (existingCornerIndex >= 0)
        {
            if (!polygon.isRemovableNode(existingCornerIndex))
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                        null,
                        session,
                        session.preview(),
                        List.of("That point is already a claim corner.")
                );
            }

            OrthogonalPolygon updatedPolygon;
            try
            {
                updatedPolygon = polygon.removeNode(existingCornerIndex);
            }
            catch (IllegalArgumentException exception)
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.INVALID_GEOMETRY,
                        null,
                        session,
                        session.preview(),
                        List.of(exception.getMessage())
                );
            }

            Integer mergedEdgeIndex = findMatchingEdgeIndex(updatedPolygon, polygon.corners().get(Math.floorMod(existingCornerIndex - 1, polygon.corners().size())), polygon.corners().get((existingCornerIndex + 1) % polygon.corners().size()));
            SegmentSelection selection = mergedEdgeIndex == null
                    ? null
                    : new SegmentSelection(resolveClaimId(session), mergedEdgeIndex, null, null, null);
            ClaimEditPreview preview = new ClaimEditPreview(
                    updatedPolygon,
                    selection,
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    List.of("Boundary segment marker removed.")
            );
            ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
            return ClaimEditResult.success(updatedSession, preview, preview.messages());
        }

        Integer edgeIndex = resolveNodeEdgeIndex(session, polygon, point);
        if (edgeIndex == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("That node point does not resolve to a single editable boundary segment.")
            );
        }

        OrthogonalPolygon updatedPolygon;
        try
        {
            updatedPolygon = polygon.insertNode(edgeIndex, point);
        }
        catch (IllegalArgumentException exception)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    session.preview(),
                    List.of(exception.getMessage())
            );
        }

        int selectedEdgeIndex = Math.min(edgeIndex + 1, updatedPolygon.edges().size() - 1);
        SegmentSelection selection = new SegmentSelection(resolveClaimId(session), selectedEdgeIndex, null, null, null);
        ClaimEditPreview preview = new ClaimEditPreview(
                updatedPolygon,
                selection,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of("Boundary segment marker added.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleCancelPath(@NotNull ClaimEditorSession session)
    {
        if (session.openPath() == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    List.of("There is no shaped path to cancel.")
            );
        }

        ClaimEditPreview existingPreview = session.preview();
        ClaimEditPreview preview = new ClaimEditPreview(
                existingPreview.polygon(),
                existingPreview.highlightedSegment(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of("Shaped path cancelled.")
        );
        ClaimEditorSession updatedSession = session.withOpenPath(null).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @Nullable Integer resolveNodeEdgeIndex(
            @NotNull ClaimEditorSession session,
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i point
    )
    {
        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.isEmpty())
        {
            return null;
        }

        // Keep node placement flexible: allow selecting any boundary segment even when an
        // active segment exists from a previous click.
        if (matches.size() == 1)
        {
            return matches.getFirst();
        }

        if (session.activeSegment() != null && matches.contains(session.activeSegment().edgeIndex()))
        {
            return session.activeSegment().edgeIndex();
        }

        return matches.getFirst();
    }

    private long resolveClaimId(@NotNull ClaimEditorSession session)
    {
        if (session.activeTarget() != null && session.activeTarget().claimId() != null)
        {
            return session.activeTarget().claimId();
        }

        return -1L;
    }

    private @NotNull SegmentExpansion expandSegment(
            @NotNull OrthogonalPolygon polygon,
            @NotNull SegmentSelection activeSegment,
            int amount)
    {
        if (amount == 0)
        {
            return new SegmentExpansion(
                    OrthogonalPolygon.validatePath(polygon.closedPath()),
                    activeSegment
            );
        }

        if (activeSegment.edgeIndex() < 0 || activeSegment.edgeIndex() >= polygon.edges().size())
        {
            throw new IllegalArgumentException("Selected segment is no longer valid.");
        }

        OrthogonalEdge2i edge = polygon.edges().get(activeSegment.edgeIndex());
        OffsetVector outward = outwardOffset(polygon, edge);
        int signedAmount = amount;
        if (activeSegment.outwardFace() != null)
        {
            BlockFace positiveFace = edge.outwardFaceForPositiveOffset();
            signedAmount = activeSegment.outwardFace() == positiveFace ? amount : -amount;
        }
        int offsetX = outward.dx() * signedAmount;
        int offsetZ = outward.dz() * signedAmount;
        OrthogonalPoint2i movedStart = new OrthogonalPoint2i(edge.start().x() + offsetX, edge.start().z() + offsetZ);
        OrthogonalPoint2i movedEnd = new OrthogonalPoint2i(edge.end().x() + offsetX, edge.end().z() + offsetZ);
        debug("expandSegment claim=%s edgeIndex=%d amount=%d edge=%s movedStart=%s movedEnd=%s polygon=%s",
                activeSegment.claimId(),
                activeSegment.edgeIndex(),
                amount,
                edge,
                movedStart,
                movedEnd,
                polygon.corners());
        OrthogonalPolygonValidationResult directResult = polygon.expandEdge(activeSegment.edgeIndex(), signedAmount);
        if (directResult.isValid() && directResult.polygon() != null)
        {
            Integer directEdgeIndex = findMatchingEdgeIndex(directResult.polygon(), movedStart, movedEnd);
            debug("expandSegment directResult polygon=%s directEdgeIndex=%s issues=%s",
                    directResult.polygon().corners(),
                    directEdgeIndex,
                    directResult.issues());
            SegmentSelection directSelection = directEdgeIndex == null
                    ? null
                    : new SegmentSelection(activeSegment.claimId(), directEdgeIndex, null, null, null);
            return new SegmentExpansion(directResult, directSelection);
        }
        OrthogonalPolygon sweep = OrthogonalPolygon.fromClosedPath(List.of(
                edge.start(),
                edge.end(),
                movedEnd,
                movedStart,
                edge.start()
        ));
        debug("expandSegment sweep=%s", sweep.corners());

        OrthogonalPolygon updatedPolygon = signedAmount > 0
                ? unionPolygons(polygon, sweep)
                : subtractPolygons(polygon, sweep);
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(updatedPolygon.closedPath());
        Integer updatedEdgeIndex = findMatchingEdgeIndex(updatedPolygon, movedStart, movedEnd);
        debug("expandSegment updatedPolygon=%s updatedEdgeIndex=%s issues=%s",
                updatedPolygon.corners(),
                updatedEdgeIndex,
                result.issues());
        SegmentSelection updatedSelection = updatedEdgeIndex == null
                ? null
                : new SegmentSelection(activeSegment.claimId(), updatedEdgeIndex, null, null, null);
        return new SegmentExpansion(result, updatedSelection);
    }

    private @NotNull OffsetVector outwardOffset(@NotNull OrthogonalPolygon polygon, @NotNull OrthogonalEdge2i edge)
    {
        int signedArea = signedArea2(polygon.corners());
        boolean counterClockwise = signedArea > 0;
        int dx = Integer.compare(edge.end().x(), edge.start().x());
        int dz = Integer.compare(edge.end().z(), edge.start().z());
        int leftDx = -dz;
        int leftDz = dx;
        return counterClockwise
                ? new OffsetVector(leftDx, leftDz)
                : new OffsetVector(dz, -dx);
    }

    private int signedArea2(@NotNull List<OrthogonalPoint2i> corners)
    {
        long area2 = 0L;
        for (int i = 0; i < corners.size(); i++)
        {
            OrthogonalPoint2i current = corners.get(i);
            OrthogonalPoint2i next = corners.get((i + 1) % corners.size());
            area2 += (long) current.x() * next.z() - (long) next.x() * current.z();
        }

        return (int) Long.signum(area2);
    }

    private @Nullable Integer findMatchingEdgeIndex(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i start,
            @NotNull OrthogonalPoint2i end)
    {
        OrthogonalEdge2i target = new OrthogonalEdge2i(start, end);
        Integer bestIndex = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestOverlap = -1;

        for (int i = 0; i < polygon.edges().size(); i++)
        {
            OrthogonalEdge2i edge = polygon.edges().get(i);
            if (target.isHorizontal() != edge.isHorizontal() || target.isVertical() != edge.isVertical())
            {
                continue;
            }

            int overlap;
            int distance;
            if (target.isHorizontal())
            {
                overlap = Math.min(target.maxX(), edge.maxX()) - Math.max(target.minX(), edge.minX());
                distance = Math.abs(target.start().z() - edge.start().z());
            }
            else
            {
                overlap = Math.min(target.maxZ(), edge.maxZ()) - Math.max(target.minZ(), edge.minZ());
                distance = Math.abs(target.start().x() - edge.start().x());
            }

            if (overlap <= 0)
            {
                continue;
            }

            if (distance < bestDistance || (distance == bestDistance && overlap > bestOverlap))
            {
                bestDistance = distance;
                bestOverlap = overlap;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private @NotNull OrthogonalPolygon subtractPolygons(
            @NotNull OrthogonalPolygon source,
            @NotNull OrthogonalPolygon cutout)
    {
        Set<OrthogonalPoint2i> occupied = occupiedPoints(source);
        occupied.removeAll(occupiedPoints(cutout));
        if (occupied.isEmpty())
        {
            throw new IllegalArgumentException("That segment move would remove the claim.");
        }

        if (!isConnected(occupied))
        {
            throw new IllegalArgumentException("That segment move would split the claim into multiple pieces.");
        }

        return polygonFromOccupiedPoints(occupied);
    }

    private @NotNull Set<OrthogonalPoint2i> occupiedPoints(@NotNull OrthogonalPolygon polygon)
    {
        Set<OrthogonalPoint2i> occupied = new HashSet<>();
        for (int x = polygon.minX(); x <= polygon.maxX(); x++)
        {
            for (int z = polygon.minZ(); z <= polygon.maxZ(); z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (polygonContains(polygon, point))
                {
                    occupied.add(point);
                }
            }
        }
        return occupied;
    }

    private boolean isConnected(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        if (occupied.isEmpty())
        {
            return true;
        }

        Set<OrthogonalPoint2i> visited = new HashSet<>();
        List<OrthogonalPoint2i> queue = new ArrayList<>();
        OrthogonalPoint2i start = occupied.iterator().next();
        queue.add(start);
        visited.add(start);

        for (int i = 0; i < queue.size(); i++)
        {
            OrthogonalPoint2i current = queue.get(i);
            for (OrthogonalPoint2i neighbor : List.of(
                    new OrthogonalPoint2i(current.x() + 1, current.z()),
                    new OrthogonalPoint2i(current.x() - 1, current.z()),
                    new OrthogonalPoint2i(current.x(), current.z() + 1),
                    new OrthogonalPoint2i(current.x(), current.z() - 1)))
            {
                if (occupied.contains(neighbor) && visited.add(neighbor))
                {
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == occupied.size();
    }

    private record OffsetVector(int dx, int dz)
    {
    }

    private record SegmentExpansion(
            @NotNull OrthogonalPolygonValidationResult result,
            @Nullable SegmentSelection selection)
    {
    }

    private static void debug(@NotNull String format, Object... args)
    {
    }
}
