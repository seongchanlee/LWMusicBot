import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import model.Commands;
import model.GuildMusicManager;
import model.WrappedAudioTrack;
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

import static helper.EmbedHelper.getTrackQueueEmbed;

public class MessageHandler extends ListenerAdapter {
    private static final String X_EMOJI = "❌";
    private static final String CHECK_EMOJI = "✅";

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

        if (!isUserInVoiceChannel(event)) {
            textChannel.sendMessage(X_EMOJI + " | You need to be in a voice channel.").queue();
            return;
        }

        if (isBotConnected(event) && !isUserInTheSameVoiceChannel(event, event.getGuild().getAudioManager())) {
            textChannel.sendMessage(X_EMOJI + " | You need to be in the same voice channel as the bot.").queue();
            return;
        }

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                WrappedAudioTrack wrappedAudioTrack = new WrappedAudioTrack(event, track);

                textChannel.sendMessage(getTrackQueueEmbed(wrappedAudioTrack)).queue();

                play(event, musicManager, wrappedAudioTrack);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
//                AudioTrack firstTrack = playlist.getSelectedTrack();
//                WrappedAudioTrack wrappedFirstTrack = new WrappedAudioTrack(event, firstTrack);
//
//                if (firstTrack == null) {
//                    firstTrack = playlist.getTracks().get(0);
//                }
//
//                textChannel.sendMessage("Adding to queue: " +
//                        firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();
//
//                play(event, musicManager, wrappedFirstTrack);

                textChannel.sendMessage(X_EMOJI + " | Playlist feature is disabled at the moment").queue();
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage(X_EMOJI + " | Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                textChannel.sendMessage(X_EMOJI + " | Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(GuildMessageReceivedEvent event, GuildMusicManager musicManager, WrappedAudioTrack track) {
        connectToVoiceChannel(event, event.getGuild().getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (musicManager.player.getPlayingTrack() == null && musicManager.scheduler.isQueueEmpty()) {
            channel.sendMessage(X_EMOJI + " | There are no tracks to skip.").queue();
        } else {
            channel.sendMessage(CHECK_EMOJI + " | Skipped current track.").queue();
            musicManager.scheduler.nextTrack();
        }
    }

    private void leaveVoice(GuildMessageReceivedEvent event) {
        TextChannel currTextChannel = event.getChannel();

        if (!isBotConnected(event)) {
            currTextChannel.sendMessage(X_EMOJI + " | Bot is not connected to any voice channel(s).").queue();
            return;
        }

        // Destroy the track
        GuildMusicManager currMusicManager = getGuildAudioPlayer(currTextChannel.getGuild());
        currMusicManager.player.destroy();

        // Disconnect
        AudioManager currAudioManager = event.getGuild().getAudioManager();
        currAudioManager.closeAudioConnection();

        currTextChannel.sendMessage(CHECK_EMOJI + " | Bot has left the channel that it was connected to.").queue();
    }

    private static void connectToVoiceChannel(GuildMessageReceivedEvent event, AudioManager audioManager) {
        VoiceChannel currChannel = getConnectedVoiceChannel(event);

        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            audioManager.openAudioConnection(currChannel);
            audioManager.setSelfMuted(false);
            audioManager.setSelfDeafened(true);
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

    private boolean isUserInVoiceChannel(GuildMessageReceivedEvent event) {
        final Member currMember = event.getMember();
        assert currMember != null;

        VoiceChannel memberVoiceChannel = Objects.requireNonNull(currMember.getVoiceState()).getChannel();

        return memberVoiceChannel != null;
    }
}
