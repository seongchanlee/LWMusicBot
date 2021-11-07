import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import model.Commands;
import model.GuildMusicManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MessageHandler extends ListenerAdapter {
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MessageHandler() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        String[] command = event.getMessage().getContentRaw().split(" ", 2);

        if ((Commands.BOT_PREFIX + Commands.PLAY).equals(command[0]) && command.length == 2) {
            loadAndPlay(event, command[1]);
        } else if ((Commands.BOT_PREFIX + Commands.VOLUME).equals(command[0]) && command.length == 2) {
            setVolume(event, command[1]);
        } else if ((Commands.BOT_PREFIX + Commands.SKIP).equals(command[0])) {
            skipTrack(event.getChannel());
        } else if ((Commands.BOT_PREFIX + Commands.LEAVE).equals(command[0])) {
            leaveVoice(event);
        }

        super.onGuildMessageReceived(event);
    }

    private void loadAndPlay(final GuildMessageReceivedEvent event, final String trackUrl) {
        final TextChannel textChannel = event.getChannel();
        final GuildMusicManager musicManager = getGuildAudioPlayer(textChannel.getGuild());

        if (isBotConnected(event) && !isUserInTheSameVoiceChannel(event, event.getGuild().getAudioManager())) {
            textChannel.sendMessage("You need to be in the same voice channel as the bot.").queue();
            return;
        }

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {

                textChannel.sendMessage("Adding to queue: " +
                        track.getInfo().title).queue();

                play(event, musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                textChannel.sendMessage("Adding to queue: " +
                        firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(event, musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                textChannel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(GuildMessageReceivedEvent event, GuildMusicManager musicManager, AudioTrack track) {
        connectToVoiceChannel(event, event.getGuild().getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void setVolume(GuildMessageReceivedEvent event, String volumeStr) {
        TextChannel currTextChannel = event.getChannel();
        int volumeInt;

        try {
            volumeInt = Integer.parseInt(volumeStr);
        } catch (NumberFormatException e) {
            currTextChannel.sendMessage("Please enter a number for the volume.").queue();
            return;
        }

        if (volumeInt < 0 || volumeInt > 200) {
            currTextChannel.sendMessage("Value for the volume must be between 0 and 200.").queue();
        }

        GuildMusicManager musicManager = getGuildAudioPlayer(currTextChannel.getGuild());
        musicManager.player.setVolume(volumeInt);

        currTextChannel.sendMessage("Volume set to "+ volumeStr +".").queue();
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (musicManager.player.getPlayingTrack() == null && musicManager.scheduler.isQueueEmpty()) {
            channel.sendMessage("There are no tracks to skip.").queue();
        } else {
            musicManager.scheduler.nextTrack();
            channel.sendMessage("Skipped current track.").queue();
        }
    }

    private void leaveVoice(GuildMessageReceivedEvent event) {
        TextChannel currTextChannel = event.getChannel();

        if (!isBotConnected(event)) {
            currTextChannel.sendMessage("Bot is not connected to any voice channel(s).").queue();
            return;
        }

        // Destroy the track
        GuildMusicManager currMusicManager = getGuildAudioPlayer(currTextChannel.getGuild());
        currMusicManager.player.destroy();

        // Disconnect
        AudioManager currAudioManager = event.getGuild().getAudioManager();
        currAudioManager.closeAudioConnection();

        currTextChannel.sendMessage("Bot has left the channel that it was connected to.").queue();
    }

    private static void connectToVoiceChannel(GuildMessageReceivedEvent event, AudioManager audioManager) {
        VoiceChannel currChannel = getConnectedVoiceChannel(event);

        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            audioManager.openAudioConnection(currChannel);
        }
    }

    private boolean isUserInTheSameVoiceChannel(GuildMessageReceivedEvent event, AudioManager audioManager) {
        final Member currMember = event.getMember();
        assert currMember != null;

        VoiceChannel memberVoiceChannel = Objects.requireNonNull(currMember.getVoiceState()).getChannel();
        assert memberVoiceChannel != null;
        String memberVoiceChannelId = memberVoiceChannel.getId();

        VoiceChannel botVoiceChannel = audioManager.getConnectedChannel();

        if (botVoiceChannel == null) {
            return false;
        }

        String botVoiceChannelId = botVoiceChannel.getId();

        return (memberVoiceChannelId != null) && (botVoiceChannelId != null) &&
                (memberVoiceChannelId.equals(botVoiceChannelId));
    }

    private static VoiceChannel getConnectedVoiceChannel(GuildMessageReceivedEvent event) {
        return Objects.requireNonNull(Objects.requireNonNull(event.getMember()).getVoiceState()).getChannel();
    }

    private boolean isBotConnected(GuildMessageReceivedEvent event) {
        AudioManager currAudioManager = event.getGuild().getAudioManager();

        return currAudioManager.isConnected();
    }
}
