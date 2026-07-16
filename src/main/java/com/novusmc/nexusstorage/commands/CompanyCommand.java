package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompanyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public CompanyCommand(Main plugin) { this.plugin = plugin; }

    private void msg(Player p, String s) {
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande joueur uniquement.");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "create"  -> handleCreate(player, args);
            case "invite"  -> handleInvite(player, args);
            case "accept"  -> handleAccept(player, args);
            case "decline" -> handleDecline(player, args);
            case "info"    -> handleInfo(player);
            case "members" -> handleMembers(player);
            case "leave"   -> handleLeave(player);
            case "kick"    -> handleKick(player, args);
            case "help"    -> sendHelp(player);
            default        -> msg(player, "&cCommande inconnue. &7/company help");
        }
        return true;
    }

    // ── create ────────────────────────────────────────────────────────────

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { msg(player, "&cUsage: /company create <nom>"); return; }

        if (plugin.getCompanyManager().getByPlayer(player.getUniqueId()) != null) {
            msg(player, plugin.getConfig().getString("messages.company-already-member",
                    "&cTu es deja dans une entreprise."));
            return;
        }

        String name = args[1];
        if (name.length() > 32) { msg(player, "&cNom trop long (max 32 caracteres)."); return; }

        double cost = plugin.getConfig().getDouble("companies.creation-cost", 50_000.0);

        Company c = plugin.getCompanyManager().create(player, name);
        if (c == null) {
            // soit pas assez d'argent (message deja envoye), soit nom pris
            if (plugin.getCompanyManager().getByName(name) != null)
                msg(player, "&cCe nom d'entreprise est deja utilise.");
            return;
        }

        String msg = plugin.getConfig().getString("messages.company-created",
                "&aEntreprise creee: &f{name} &7(cout: &f{cost}$&7)")
                .replace("{name}", c.getName())
                .replace("{cost}", String.format("%.0f", cost));
        msg(player, msg);
    }

    // ── invite ────────────────────────────────────────────────────────────

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) { msg(player, "&cUsage: /company invite <joueur>"); return; }

        Company c = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        if (c == null) { msg(player, "&cTu n'es dans aucune entreprise."); return; }
        if (!c.isManager(player.getUniqueId())) {
            msg(player, plugin.getConfig().getString("messages.company-no-permission",
                    "&cPermission insuffisante."));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { msg(player, "&cJoueur introuvable ou hors ligne."); return; }
        if (target.equals(player)) { msg(player, "&cTu ne peux pas t'inviter toi-meme."); return; }

        boolean ok = plugin.getCompanyManager().invite(c, target.getUniqueId(), player);
        if (!ok) { msg(player, "&cImpossible d'inviter ce joueur (deja membre ou dans une autre entreprise)."); return; }

        msg(player, plugin.getConfig().getString("messages.company-invitation-sent",
                "&aInvitation envoyee a &f{player}&a.")
                .replace("{player}", target.getName()));

        target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.company-invitation-received",
                        "&6[NexusStorage] &eInvitation de &f{company}&e. /company accept {company}")
                        .replace("{company}", c.getName())));
    }

    // ── accept ────────────────────────────────────────────────────────────

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            // Lister les invitations en attente
            List<Company> pending = plugin.getCompanyManager().pendingInvitations(player.getUniqueId());
            if (pending.isEmpty()) { msg(player, "&7Aucune invitation en attente."); return; }
            msg(player, "&6Invitations en attente:");
            pending.forEach(c -> msg(player, "  &e• " + c.getName() + " &7(/company accept " + c.getName() + ")"));
            return;
        }

        if (plugin.getCompanyManager().getByPlayer(player.getUniqueId()) != null) {
            msg(player, plugin.getConfig().getString("messages.company-already-member",
                    "&cTu es deja dans une entreprise."));
            return;
        }

        boolean ok = plugin.getCompanyManager().accept(player.getUniqueId(), args[1]);
        if (!ok) {
            msg(player, plugin.getConfig().getString("messages.company-invitation-expired",
                    "&cL'invitation a expire ou est invalide."));
            return;
        }

        Company c = plugin.getCompanyManager().getByName(args[1]);
        msg(player, plugin.getConfig().getString("messages.company-joined",
                "&aTu as rejoint l'entreprise: &f{company}")
                .replace("{company}", c != null ? c.getName() : args[1]));
    }

    // ── decline ───────────────────────────────────────────────────────────

    private void handleDecline(Player player, String[] args) {
        if (args.length < 2) { msg(player, "&cUsage: /company decline <nom>"); return; }
        plugin.getCompanyManager().decline(player.getUniqueId(), args[1]);
        msg(player, "&7Invitation refusee.");
    }

    // ── info ──────────────────────────────────────────────────────────────

    private void handleInfo(Player player) {
        Company c = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        if (c == null) { msg(player, "&cTu n'es dans aucune entreprise."); return; }

        String ownerName = Bukkit.getOfflinePlayer(c.getOwner()).getName();
        msg(player, "&6&l── Entreprise: " + c.getName() + " ──");
        msg(player, "&7Proprietaire : &f" + (ownerName != null ? ownerName : c.getOwner()));
        msg(player, "&7Membres       : &f" + c.getMembers().size());
        msg(player, "&7Ton role      : &f" + c.getRole(player.getUniqueId()));
    }

    // ── members ───────────────────────────────────────────────────────────

    private void handleMembers(Player player) {
        Company c = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        if (c == null) { msg(player, "&cTu n'es dans aucune entreprise."); return; }

        msg(player, "&6Membres de &f" + c.getName() + "&6:");
        for (Map.Entry<java.util.UUID, Company.Role> e : c.getMembers().entrySet()) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            msg(player, "  &e• " + (name != null ? name : e.getKey()) + " &7[" + e.getValue() + "]");
        }
    }

    // ── leave ─────────────────────────────────────────────────────────────

    private void handleLeave(Player player) {
        Company c = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        if (c == null) { msg(player, "&cTu n'es dans aucune entreprise."); return; }
        if (c.isOwner(player.getUniqueId())) {
            msg(player, "&cLe proprietaire ne peut pas quitter l'entreprise. Utilise /company disband.");
            return;
        }
        plugin.getCompanyManager().leave(player.getUniqueId());
        msg(player, plugin.getConfig().getString("messages.company-left", "&aTu as quitte l'entreprise."));
    }

    // ── kick ──────────────────────────────────────────────────────────────

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) { msg(player, "&cUsage: /company kick <joueur>"); return; }
        Company c = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        if (c == null) { msg(player, "&cTu n'es dans aucune entreprise."); return; }

        Player target = Bukkit.getPlayer(args[1]);
        java.util.UUID targetUuid = target != null ? target.getUniqueId()
                : Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        boolean ok = plugin.getCompanyManager().kick(c, targetUuid, player.getUniqueId());
        if (!ok) { msg(player, "&cImpossible d'expulser ce joueur."); return; }
        msg(player, "&a" + args[1] + " a ete expulse de l'entreprise.");
        if (target != null) target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cTu as ete expulse de l'entreprise &f" + c.getName() + "&c."));
    }

    // ── help ─────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        msg(player, "&6&l── Entreprises NexusStorage ──");
        msg(player, "&e/company create <nom>    &7Creer une entreprise");
        msg(player, "&e/company invite <joueur> &7Inviter un joueur");
        msg(player, "&e/company accept [nom]    &7Accepter une invitation");
        msg(player, "&e/company decline <nom>   &7Refuser une invitation");
        msg(player, "&e/company info            &7Infos de ton entreprise");
        msg(player, "&e/company members         &7Liste des membres");
        msg(player, "&e/company leave           &7Quitter l'entreprise");
        msg(player, "&e/company kick <joueur>   &7Expulser un membre");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1)
            options.addAll(List.of("create", "invite", "accept", "decline", "info", "members", "leave", "kick", "help"));
        return options.stream().filter(o -> o.startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
