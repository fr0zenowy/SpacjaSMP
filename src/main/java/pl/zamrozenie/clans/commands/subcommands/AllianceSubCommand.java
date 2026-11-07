package pl.zamrozenie.clans.commands.subcommands;

import pl.zamrozenie.clans.commands.SubCommand;
import pl.zamrozenie.clans.managers.*;
import pl.zamrozenie.clans.models.*;
import pl.zamrozenie.clans.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class AllianceSubCommand implements SubCommand {
    
    private final ClanManager clanManager;
    private final AllianceManager allianceManager;
    
    public AllianceSubCommand(ClanManager clanManager, AllianceManager allianceManager) {
        this.clanManager = clanManager;
        this.allianceManager = allianceManager;
    }
    
    @Override
    public String getName() {
        return "sojusz";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("alliance", "ally");
    }
    
    @Override
    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            showAllianceHelp(player);
            return;
        }
        
        String subAction = args[0].toLowerCase();
        
        switch (subAction) {
            case "wyslij", "send" -> handleSendAlliance(player, args);
            case "akceptuj", "accept" -> handleAcceptAlliance(player, args);
            case "rozwiaz", "break", "usun" -> handleBreakAlliance(player, args);
            case "lista", "list" -> handleListAlliances(player);
            default -> showAllianceHelp(player);
        }
    }
    
    private void handleSendAlliance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan sojusz wyslij <tag>"));
            return;
        }
        
        String targetTag = args[1];
        Clan senderClan = clanManager.getPlayerClan(player.getUniqueId());
        Clan targetClan = clanManager.getClanByTag(targetTag);
        
        String error = allianceManager.sendAllianceRequest(player, targetTag);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        player.sendMessage(MessageUtil.format("&8[&3⚔&8] &7Wysłano propozycję sojuszu do klanu " + 
            MessageUtil.formatClanTag(targetTag) + "&7!"));
        
        // Powiadom liderów i adminów klanu docelowego
        if (targetClan != null) {
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (targetClan.hasMember(member.getUniqueId())) {
                    ClanMember clanMember = targetClan.getMember(member.getUniqueId());
                    if (clanMember != null && clanMember.canManageMembers()) {
                        sendAllianceInvite(member, senderClan);
                    }
                }
            }
        }
    }
    
    private void sendAllianceInvite(Player target, Clan senderClan) {
        target.sendMessage(MessageUtil.divider());
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.format("&7Klan " + MessageUtil.formatClanTag(senderClan.getTag()) +
            " &7" + senderClan.getName() + " wysyła propozycję sojuszu!"));
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.format("&7Wpisz &f/klan sojusz akceptuj " + senderClan.getTag() + " &7aby zaakceptować"));
        target.sendMessage(MessageUtil.format("&7Wpisz &f/klan sojusz odrzuc " + senderClan.getTag() + " &7aby odrzucić"));
        target.sendMessage(MessageUtil.format(""));
        target.sendMessage(MessageUtil.divider());
    }
    
    private void handleAcceptAlliance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan sojusz akceptuj <tag>"));
            return;
        }
        
        String senderTag = args[1];
        Clan accepterClan = clanManager.getPlayerClan(player.getUniqueId());
        Clan senderClan = clanManager.getClanByTag(senderTag);
        
        String error = allianceManager.acceptAlliance(player, senderTag);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        // Powiadom oba klany
        if (accepterClan != null && senderClan != null) {
            String message = MessageUtil.format("&8[&a✔&8] &7Klan " + 
                MessageUtil.formatClanTag(accepterClan.getTag()) + 
                " &7zawarł sojusz z " + 
                MessageUtil.formatClanTag(senderClan.getTag()) + "&7!");
            
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (accepterClan.hasMember(member.getUniqueId()) || senderClan.hasMember(member.getUniqueId())) {
                    member.sendMessage(message);
                }
            }
        }
    }
    
    private void handleBreakAlliance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Użycie: &f/klan sojusz rozwiaz <tag>"));
            return;
        }
        
        String targetTag = args[1];
        Clan breakerClan = clanManager.getPlayerClan(player.getUniqueId());
        Clan targetClan = clanManager.getClanByTag(targetTag);
        
        String error = allianceManager.breakAlliance(player, targetTag);
        
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        
        // Powiadom oba klany
        if (breakerClan != null && targetClan != null) {
            String message = MessageUtil.format("&8[&c✘&8] &7Sojusz między " + 
                MessageUtil.formatClanTag(breakerClan.getTag()) + 
                " &7a " + 
                MessageUtil.formatClanTag(targetClan.getTag()) + 
                " &7został rozwiązany!");
            
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (breakerClan.hasMember(member.getUniqueId()) || targetClan.hasMember(member.getUniqueId())) {
                    member.sendMessage(message);
                }
            }
        }
    }
    
    private void handleListAlliances(Player player) {
        Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        
        if (clan == null) {
            player.sendMessage(MessageUtil.error("Nie należysz do żadnego klanu!"));
            return;
        }
        
        List<Clan> allies = allianceManager.getAlliedClans(clan);
        List<Alliance> pending = allianceManager.getPendingAllianceRequests(clan);
        
        player.sendMessage(MessageUtil.shortDivider());
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&3&lSojusze klanu " + MessageUtil.formatClanTag(clan.getTag())));
        player.sendMessage(MessageUtil.format(""));
        
        if (allies.isEmpty() && pending.isEmpty()) {
            player.sendMessage(MessageUtil.format("&7Brak sojuszy"));
        } else {
            if (!allies.isEmpty()) {
                player.sendMessage(MessageUtil.format("&aAktywne sojusze:"));
                for (Clan ally : allies) {
                    player.sendMessage(MessageUtil.format("&8» " + 
                        MessageUtil.formatClanTag(ally.getTag()) + " &7" + ally.getName()));
                }
            }
            
            if (!pending.isEmpty()) {
                player.sendMessage(MessageUtil.format(""));
                player.sendMessage(MessageUtil.format("&eOczekujące zaproszenia:"));
                for (Alliance alliance : pending) {
                    int otherClanId = alliance.getOtherClanId(clan.getId());
                    Clan otherClan = clanManager.getClanByTag(String.valueOf(otherClanId));
                    if (otherClan != null) {
                        player.sendMessage(MessageUtil.format("&8» " + 
                            MessageUtil.formatClanTag(otherClan.getTag()) + " &7" + otherClan.getName()));
                    }
                }
            }
        }
        
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.divider());
    }
    
    private void showAllianceHelp(Player player) {
        player.sendMessage(MessageUtil.shortDivider());
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&3&lSojusze"));
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.format("&8» &7/klan sojusz &fwyslij <tag> &8- &7Wysyła propozycję"));
        player.sendMessage(MessageUtil.format("&8» &7/klan sojusz &fakceptuj <tag> &8- &7Akceptuje sojusz"));
        player.sendMessage(MessageUtil.format("&8» &7/klan sojusz &frozwiaz <tag> &8- &7Rozwiązuje sojusz"));
        player.sendMessage(MessageUtil.format("&8» &7/klan sojusz &flista &8- &7Lista sojuszy"));
        player.sendMessage(MessageUtil.format(""));
        player.sendMessage(MessageUtil.divider());
    }
    
    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return List.of("wyslij", "akceptuj", "rozwiaz", "lista");
        } else if (args.length == 2) {
            List<String> tags = new ArrayList<>();
            for (Clan clan : clanManager.getAllClans()) {
                if (clan.getTag().toLowerCase().startsWith(args[1].toLowerCase())) {
                    tags.add(clan.getTag());
                }
            }
            return tags;
        }
        return Collections.emptyList();
    }
}