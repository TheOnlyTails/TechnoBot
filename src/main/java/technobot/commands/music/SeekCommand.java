package technobot.commands.music;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import technobot.TechnoBot;
import technobot.commands.Category;
import technobot.commands.Command;
import technobot.handlers.MusicHandler;
import technobot.listeners.MusicListener;
import technobot.util.embeds.EmbedUtils;

/**
 * Command that jumps to a specified position in the current track.
 *
 * @author TechnoVision
 */
public class SeekCommand extends Command {

    public SeekCommand(TechnoBot bot) {
        super(bot);
        this.name = "seek";
        this.description = "Jumps to a specified position in the current track.";
        this.category = Category.MUSIC;
        this.args.add(new OptionData(OptionType.STRING, "position", "Seeks to a certain point", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        MusicHandler music = bot.musicListener.getMusic(event, false);
        if (music == null) return;

        String position = event.getOption("position").getAsString();
        try {
            long pos;
            if (position.contains(":")) {
                // Build pos using timestamp
                String[] timestamp = position.split(":");
                int minutes = Integer.parseInt(timestamp[0]) * 60;
                int seconds = Integer.parseInt(timestamp[1]);
                if (minutes < 0 || seconds < 0) throw new NumberFormatException();
                if (timestamp[1].length() == 1) {
                    seconds *= 10;
                }
                pos = (minutes + seconds) * 1000L;
            } else {
                // Build pos using seconds
                pos = Integer.parseInt(position) * 1000L;
            }

            // Make sure pos is not longer than track
            if (pos >= music.getQueue().getFirst().getDuration()) {
                String text = "Time cannot be longer than the song!";
                event.getHook().sendMessageEmbeds(EmbedUtils.createError(text)).queue();
                return;
            }

            // Set position and send message
            music.seek(pos);
            String text = ":fast_forward: Set position to `" + MusicListener.formatTrackLength(pos) + "`";
            event.getHook().sendMessageEmbeds(EmbedUtils.createDefault(text)).queue();

        } catch ( NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Invalid timestamps
            String text = "That is not a valid timestamp!";
            event.getHook().sendMessageEmbeds(EmbedUtils.createError(text)).queue();
        }
    }
}
