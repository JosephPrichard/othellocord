package discord

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
	"othellocord/app/othello"
)

var GreenColor = 0x00ff00

func CreateGameStartEmbed(game Game) *discordgo.MessageEmbed {
	desc := fmt.Sprintf(
		"Black: %s\n White: %s\n Use `/view` to view the game and use `/move` to make a move.",
		game.BlackPlayer.Name,
		game.WhitePlayer.Name)
	return &discordgo.MessageEmbed{
		Title:       "Game Started!",
		Description: desc,
		Color:       GreenColor,
	}
}
func CreateSimulationStartEmbed(game Game) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("Black: %s\n White: %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	return &discordgo.MessageEmbed{
		Title:       "Simulation started!",
		Description: desc,
		Color:       GreenColor,
	}
}

func CreateGameMoveEmbed(game Game, move othello.Tile) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%sYour opponent has moved: %s", getScoreText(game), move.String())
	footer := "White to move"
	if game.IsBlackMove {
		footer = "Black to move"
	}
	return &discordgo.MessageEmbed{
		Title:       fmt.Sprintf("Your game with %s", game.OtherPlayer().Name),
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenColor,
	}
}

func CreateSimulationEmbed(game Game, move othello.Tile) *discordgo.MessageEmbed {
	title := fmt.Sprintf("%s vs %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	desc := fmt.Sprintf("%s%s has moved: %s", getScoreText(game), game.OtherPlayer().Name, move.String())
	footer := "White to move"
	if game.IsBlackMove {
		footer = "Black to move"
	}
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenColor,
	}
}

func CreateGameEmbed(game Game) *discordgo.MessageEmbed {
	title := fmt.Sprintf("%s vs %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	desc := fmt.Sprintf("%s%s to move", getScoreText(game), game.CurrentPlayer().Name)
	footer := "White to move"
	if game.IsBlackMove {
		footer = "Black to move"
	}
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenColor,
	}
}

func CreateAnalysisEmbed(game Game, level int64) *discordgo.MessageEmbed {
	desc := getScoreText(game)
	title := fmt.Sprintf("Game Analysis using bot level %d", level)
	footer := "Positive heuristics are better for black, and negative heuristics are better for white"
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
	}
}

func CreateGameOverEmbed(result GameResult, statsResult StatsResult, move othello.Tile, game Game) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%s%s\n%s",
		getMoveMessage(result.Winner, move.String()),
		getScoreMessage(game.WhiteScore(), game.BlackScore()),
		getStatsMessage(result, statsResult),
	)
	return &discordgo.MessageEmbed{
		Title:       "Game has ended",
		Description: desc,
	}
}

func CreateForfeitEmbed(result GameResult, statsResult StatsResult) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%s\n%s",
		getForfeitMessage(result.Winner),
		getStatsMessage(result, statsResult),
	)
	return &discordgo.MessageEmbed{
		Title:       "Game has ended",
		Description: desc,
		Color:       GreenColor,
	}
}

func CreateResultSimulationEmbed(game Game, move othello.Tile) *discordgo.MessageEmbed {
	result := game.CreateResult()
	desc := fmt.Sprintf("%s%s",
		getMoveMessage(result.Winner, move.String()),
		getScoreMessage(game.WhiteScore(), game.BlackScore()),
	)
	return &discordgo.MessageEmbed{
		Title:       "Simulation has ended",
		Description: desc,
		Color:       GreenColor,
	}
}

func getScoreText(game Game) string {
	return fmt.Sprintf("Black: %d points\nWhite: %d points\n", game.BlackScore(), game.WhiteScore())
}

func getStatsMessage(gameRes GameResult, statsRes StatsResult) string {
	return fmt.Sprintf("%s's new rating is %d (%s) \n %s's new rating is %d (%s)\n",
		gameRes.Winner.Name,
		int(statsRes.WinnerElo),
		statsRes.FormatWinnerEloDiff(),
		gameRes.Loser.Name,
		int(statsRes.LoserElo),
		statsRes.FormatLoserEloDiff())
}

func getForfeitMessage(winner Player) string {
	return fmt.Sprintf("%s won by forfeit\n", winner.Name)
}

func getScoreMessage(whiteScore, blackScore int) string {
	return fmt.Sprintf("Score: %d - %d\n", blackScore, whiteScore)
}

func getMoveMessage(winner Player, move string) string {
	return fmt.Sprintf("%s won with %s\n", winner.Name, move)
}
