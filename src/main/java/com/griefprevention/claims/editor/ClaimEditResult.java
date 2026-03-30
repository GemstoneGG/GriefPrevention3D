package com.griefprevention.claims.editor;

import me.ryanhamshire.GriefPrevention.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Output from applying an editor intent.
 */
public record ClaimEditResult(
        boolean success,
        @Nullable ClaimEditFailureType failureType,
        @Nullable Messages fallbackMessage,
        @NotNull ClaimEditorSession session,
        @NotNull ClaimEditPreview preview,
        @NotNull List<String> messages
)
{
    public ClaimEditResult
    {
        messages = List.copyOf(messages);
    }

    public static @NotNull ClaimEditResult success(
            @NotNull ClaimEditorSession session,
            @NotNull ClaimEditPreview preview,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditResult(true, null, null, session, preview, messages);
    }

    public static @NotNull ClaimEditResult failure(
            @NotNull ClaimEditFailureType failureType,
            @Nullable Messages fallbackMessage,
            @NotNull ClaimEditorSession session,
            @NotNull ClaimEditPreview preview,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditResult(false, failureType, fallbackMessage, session, preview, messages);
    }
}
