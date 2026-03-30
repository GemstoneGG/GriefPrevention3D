package com.griefprevention.api;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Command context passed to addon-defined /claim and /aclaim subcommands.
 */
public final class ClaimCommandContext {

    private final @NotNull CommandSender sender;
    private final @NotNull String rootCommand;
    private final @NotNull String subcommand;
    private final @NotNull String[] args;
    private final @Nullable Claim selectedOrCurrentClaim;

    public ClaimCommandContext(
            @NotNull CommandSender sender,
            @NotNull String rootCommand,
            @NotNull String subcommand,
            @NotNull String[] args,
            @Nullable Claim selectedOrCurrentClaim)
    {
        this.sender = sender;
        this.rootCommand = rootCommand;
        this.subcommand = subcommand;
        this.args = args.clone();
        this.selectedOrCurrentClaim = selectedOrCurrentClaim;
    }

    public @NotNull CommandSender getSender() {
        return this.sender;
    }

    public @NotNull String getRootCommand() {
        return this.rootCommand;
    }

    public @NotNull String getSubcommand() {
        return this.subcommand;
    }

    public @NotNull String[] getArgs() {
        return this.args.clone();
    }

    public @Nullable Claim getSelectedOrCurrentClaim() {
        return this.selectedOrCurrentClaim;
    }
}
