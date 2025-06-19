/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import engine.OthelloBoard;
import engine.Tile;
import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import services.GameService;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class AutoCompleteHandler {
    private GameService gameService;

    public void handleMove(CommandAutoCompleteInteraction interaction) {
        Player player = new Player(interaction.getUser());

        Game game = gameService.getGame(player);
        if (game != null) {
            List<Tile> moves = game.findPotentialMoves();

            // don't display duplicate moves
            boolean[][] duplicate = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];

            List<Command.Choice> choices = new ArrayList<>();
            for (Tile tile : moves) {
                int row = tile.row();
                int col = tile.col();

                if (!duplicate[row][col]) {
                    choices.add(new Command.Choice(tile.toString(), tile.toString()));
                }
                duplicate[row][col] = true;
            }

            interaction.replyChoices(choices).queue();
        } else {
            interaction.replyChoices().queue();
        }
    }
}
