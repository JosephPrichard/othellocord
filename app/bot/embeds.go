package bot

import (
	"bytes"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"image"
	"image/jpeg"
	"log/slog"
	"othellocord/app/othello"
)

var GreenColor = 0x00ff00

func createStringResponse(msg string) *discordgo.InteractionResponse {
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: msg,
		},
	}
}

func createStringEdit(msg string) *discordgo.WebhookEdit {
	return &discordgo.WebhookEdit{Content: &msg}
}

func addEmbedFiles(embed *discordgo.MessageEmbed, img image.Image) []*discordgo.File {
	var files []*discordgo.File

	if img != nil {
		var buf bytes.Buffer
		if err := jpeg.Encode(&buf, img, nil); err != nil {
			// we can't really do anything if this fails, it would be an issue with the board renderer
			slog.Error("failed to encode image", "error", err)
			return nil
		}
		file := &discordgo.File{
			Name:        "image.png",
			ContentType: "image/png",
			Reader:      &buf,
		}
		files = append(files, file)

		// this removes any previous attachments to the embed and makes sure it matches the file being sent in the response
		embed.Image = &discordgo.MessageEmbedImage{URL: "attachment://image.png"}
	}

	return files
}

func createEmbedResponse(embed *discordgo.MessageEmbed, img image.Image) *discordgo.InteractionResponse {
	files := addEmbedFiles(embed, img)
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Embeds: []*discordgo.MessageEmbed{embed},
			Files:  files,
		},
	}
}

func createAutocompleteResponse(choices []*discordgo.ApplicationCommandOptionChoice) *discordgo.InteractionResponse {
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionApplicationCommandAutocompleteResult,
		Data: &discordgo.InteractionResponseData{
			Choices: choices,
		},
	}
}

var EmptyEdit = ""

func createEmbedEdit(embed *discordgo.MessageEmbed, img image.Image) *discordgo.WebhookEdit {
	files := addEmbedFiles(embed, img)
	return &discordgo.WebhookEdit{
		Embeds:  &[]*discordgo.MessageEmbed{embed},
		Files:   files,
		Content: &EmptyEdit,
	}
}

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

func CreateAnalysisEmbed(game Game, level int) *discordgo.MessageEmbed {
	desc := getScoreText(game)
	title := fmt.Sprintf("Game Analysis using bot level %d", level)
	footer := "Positive heuristics are better for black, and negative heuristics are better for white"
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
	}
}

func CreateGameOverEmbed(game Game, result GameResult, statsResult StatsResult, move othello.Tile) *discordgo.MessageEmbed {
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
