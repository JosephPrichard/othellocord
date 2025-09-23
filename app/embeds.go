package app

import (
	"bytes"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"image"
	"image/jpeg"
	"log/slog"
	"strconv"
	"strings"
)

const GreenEmbed = 0x00ff00

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
			// we can't do anything if this fails, it would be an issue with the OthelloBoard renderer
			slog.Error("failed to encode image", "err", err)
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
	return createComponentResponse(embed, img, nil)
}

func createComponentResponse(embed *discordgo.MessageEmbed, img image.Image, components []discordgo.MessageComponent) *discordgo.InteractionResponse {
	files := addEmbedFiles(embed, img)
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Embeds:     []*discordgo.MessageEmbed{embed},
			Files:      files,
			Components: components,
		},
	}
}

func createMoveErrorResp(err error, moveStr string) *discordgo.InteractionResponse {
	var resp *discordgo.InteractionResponse
	if errors.Is(err, ErrGameNotFound) {
		resp = createStringResponse("You're not currently playing a OthelloGame.")
	} else if errors.Is(err, ErrInvalidMove) {
		resp = createStringResponse(fmt.Sprintf("Can't make a Move to %s.", moveStr))
	} else if errors.Is(err, ErrTurn) {
		resp = createStringResponse("It isn't your turn.")
	}
	return resp
}

func createEmbedSend(embed *discordgo.MessageEmbed, img image.Image) *discordgo.MessageSend {
	files := addEmbedFiles(embed, img)
	return &discordgo.MessageSend{
		Embeds: []*discordgo.MessageEmbed{embed},
		Files:  files,
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

const SimPauseKey = "sim-pause-key"
const SimStopKey = "sim-stop-key"

func createSimulationActionRow(simulationID string, isPaused bool) []discordgo.MessageComponent {
	stopID := fmt.Sprintf("%s+%s", SimStopKey, simulationID)
	pauseID := fmt.Sprintf("%s+%s", SimPauseKey, simulationID)

	components := []discordgo.MessageComponent{discordgo.Button{CustomID: stopID, Label: "Stop", Style: discordgo.DangerButton}}
	if isPaused {
		components = append(components, discordgo.Button{CustomID: pauseID, Label: "Play", Style: discordgo.PrimaryButton})
	} else {
		components = append(components, discordgo.Button{CustomID: pauseID, Label: "Pause", Style: discordgo.PrimaryButton})
	}

	if components != nil {
		return []discordgo.MessageComponent{discordgo.ActionsRow{Components: components}}
	}
	return nil
}

var empty = ""

func createEmbedEdit(embed *discordgo.MessageEmbed, img image.Image) *discordgo.WebhookEdit {
	files := addEmbedFiles(embed, img)
	return &discordgo.WebhookEdit{
		Embeds:      &[]*discordgo.MessageEmbed{embed},
		Attachments: &[]*discordgo.MessageAttachment{},
		Files:       files,
		Content:     &empty,
	}
}

func createEmbedTextEdit(edit string) *discordgo.WebhookEdit {
	return &discordgo.WebhookEdit{
		Embeds:      &[]*discordgo.MessageEmbed{},
		Attachments: &[]*discordgo.MessageAttachment{},
		Content:     &edit,
	}
}

func createGameStartEmbed(game OthelloGame) *discordgo.MessageEmbed {
	desc := fmt.Sprintf(
		"Black: %s\n White: %s\n Use `/view` to view the OthelloGame and use `/Move` to make a Move.",
		game.BlackPlayer.Name,
		game.WhitePlayer.Name)
	return &discordgo.MessageEmbed{
		Title:       "OthelloGame Started!",
		Description: desc,
		Color:       GreenEmbed,
	}
}

func createSimulationStartEmbed(game OthelloGame) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("Black: %s\n White: %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	return &discordgo.MessageEmbed{
		Title:       "Simulation started!",
		Description: desc,
		Color:       GreenEmbed,
	}
}

func createGameMoveEmbed(game OthelloGame, move Tile) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%sYour opponent has moved: %s", getScoreText(game), move.String())
	footer := "White to Move"
	if game.Board.IsBlackMove {
		footer = "Black to Move"
	}
	return &discordgo.MessageEmbed{
		Title:       fmt.Sprintf("Your OthelloGame with %s", game.OtherPlayer().Name),
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenEmbed,
	}
}

func createStepEdit(renderer Renderer, step SimStep) *discordgo.WebhookEdit {
	var edit *discordgo.WebhookEdit
	img := renderer.DrawBoardMoves(step.Game.Board, step.Game.Board.FindCurrentMoves())
	if !step.Ok {
		edit = createEmbedTextEdit("Failed to retrieve simulation data from engine.")
	} else if step.Finished {
		updtEmbed := createSimulationEndEmbed(step.Game, step.Move)
		edit = createEmbedEdit(updtEmbed, img)
		edit.Components = &[]discordgo.MessageComponent{}
	} else {
		updtEmbed := createSimulationEmbed(step.Game, step.Move)
		edit = createEmbedEdit(updtEmbed, img)
	}
	return edit
}

func createSimulationEmbed(game OthelloGame, move Tile) *discordgo.MessageEmbed {
	title := fmt.Sprintf("%s vs %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	desc := fmt.Sprintf("%s%s has moved: %s", getScoreText(game), game.OtherPlayer().Name, move.String())
	footer := "White to Move"
	if game.Board.IsBlackMove {
		footer = "Black to Move"
	}
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenEmbed,
	}
}

func createGameEmbed(game OthelloGame) *discordgo.MessageEmbed {
	title := fmt.Sprintf("%s vs %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
	desc := fmt.Sprintf("%s%s to Move", getScoreText(game), game.CurrentPlayer().Name)
	footer := "White to Move"
	if game.Board.IsBlackMove {
		footer = "Black to Move"
	}
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
		Color:       GreenEmbed,
	}
}

func createAnalysisEmbed(game OthelloGame, level uint64) *discordgo.MessageEmbed {
	desc := getScoreText(game)
	title := fmt.Sprintf("OthelloGame Analysis using service level %d", level)
	footer := "Positive heuristics are better for black, and negative heuristics are better for white"
	return &discordgo.MessageEmbed{
		Title:       title,
		Description: desc,
		Footer:      &discordgo.MessageEmbedFooter{Text: footer},
	}
}

func createGameOverEmbed(game OthelloGame, result GameResult, statsResult StatsResult, move Tile) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%s%s\n%s",
		getMoveMessage(result.Winner, move.String()),
		getScoreMessage(game.Board.WhiteScore(), game.Board.BlackScore()),
		getStatsMessage(result, statsResult),
	)
	return &discordgo.MessageEmbed{Title: "OthelloGame has ended", Description: desc}
}

func createForfeitEmbed(result GameResult, statsResult StatsResult) *discordgo.MessageEmbed {
	desc := fmt.Sprintf("%s\n%s",
		getForfeitMessage(result.Winner),
		getStatsMessage(result, statsResult),
	)
	return &discordgo.MessageEmbed{
		Title:       "OthelloGame has ended",
		Description: desc,
		Color:       GreenEmbed,
	}
}

func createSimulationEndEmbed(game OthelloGame, move Tile) *discordgo.MessageEmbed {
	result := game.CreateResult()
	desc := fmt.Sprintf("%s%s",
		getMoveMessage(result.Winner, move.String()),
		getScoreMessage(game.Board.WhiteScore(), game.Board.BlackScore()),
	)
	return &discordgo.MessageEmbed{
		Title:       "Simulation has ended",
		Description: desc,
		Color:       GreenEmbed,
	}
}

func createStatsEmbed(user discordgo.User, stats Stats) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title: fmt.Sprintf("%s's stats", user.Username),
		Fields: []*discordgo.MessageEmbedField{
			{Name: "Rating", Value: fmt.Sprintf("%0.2f", stats.Elo), Inline: false},
			{Name: "Win Rate", Value: stats.WinRate(), Inline: false},
			{Name: "Won", Value: strconv.Itoa(stats.Won), Inline: true},
			{Name: "Lost", Value: strconv.Itoa(stats.Lost), Inline: true},
			{Name: "Drawn", Value: strconv.Itoa(stats.Drawn), Inline: true},
		},
		Thumbnail: &discordgo.MessageEmbedThumbnail{
			URL:    user.AvatarURL("1024"),
			Width:  1024,
			Height: 1024,
		},
		Color: GreenEmbed,
	}
}

func createLeaderboardEmbed(stats []Stats) *discordgo.MessageEmbed {
	var desc strings.Builder
	desc.WriteString("```\n")
	for i, stats := range stats {
		desc.WriteString(rightPad(fmt.Sprintf("%d)", i+1), 4))
		desc.WriteString(leftPad(stats.Player.Name, 32))
		desc.WriteString(leftPad(fmt.Sprintf("%.2f", stats.Elo), 12))
		desc.WriteString("\n")
	}
	desc.WriteString("```")

	return &discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       GreenEmbed,
		Footer: &discordgo.MessageEmbedFooter{
			Text: fmt.Sprintf("Top %d rated players", LeaderboardSize),
		},
	}

}

func getScoreText(game OthelloGame) string {
	return fmt.Sprintf("Black: %d points\nWhite: %d points\n", game.Board.BlackScore(), game.Board.WhiteScore())
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
