package app

import (
	"bytes"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"image"
	"image/jpeg"
	"log/slog"
	"strconv"
	"strings"
)

const GreenEmbed = 0x00ff00

func makeStringResponse(msg string) *discordgo.InteractionResponse {
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: msg,
		},
	}
}

func makeStringEdit(msg string) *discordgo.WebhookEdit {
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

func makeEmbedResponse(embed *discordgo.MessageEmbed, img image.Image) *discordgo.InteractionResponse {
	return makeComponentResponse(embed, img, nil)
}

func makeComponentResponse(embed *discordgo.MessageEmbed, img image.Image, components []discordgo.MessageComponent) *discordgo.InteractionResponse {
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

func makeEmbedSend(embed *discordgo.MessageEmbed, img image.Image, target Player) *discordgo.MessageSend {
	files := addEmbedFiles(embed, img)
	return &discordgo.MessageSend{
		Embeds:  []*discordgo.MessageEmbed{embed},
		Files:   files,
		Content: fmt.Sprintf("<@%s>", target.ID),
	}
}

func makeStringSend(text string) *discordgo.MessageSend {
	return &discordgo.MessageSend{Content: text}
}

func makeAutocompleteResponse(choices []*discordgo.ApplicationCommandOptionChoice) *discordgo.InteractionResponse {
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionApplicationCommandAutocompleteResult,
		Data: &discordgo.InteractionResponseData{
			Choices: choices,
		},
	}
}

const SimPauseKey = "sim-pause-key"
const SimStopKey = "sim-stop-key"

func makeSimulationActionRow(simulationID string, isPaused bool) []discordgo.MessageComponent {
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

func makeEmbedEdit(embed *discordgo.MessageEmbed, img image.Image) *discordgo.WebhookEdit {
	files := addEmbedFiles(embed, img)
	return &discordgo.WebhookEdit{
		Embeds:      &[]*discordgo.MessageEmbed{embed},
		Attachments: &[]*discordgo.MessageAttachment{},
		Files:       files,
		Content:     &empty,
	}
}

func makeEmbedTextEdit(edit string) *discordgo.WebhookEdit {
	return &discordgo.WebhookEdit{
		Embeds:      &[]*discordgo.MessageEmbed{},
		Attachments: &[]*discordgo.MessageAttachment{},
		Content:     &edit,
	}
}

func makeGameStartEmbed(game OthelloGame) *discordgo.MessageEmbed {
	desc := fmt.Sprintf(
		"Black: %s\n White: %s\n Use `/view` to view the game and use `/move` to make a move.",
		game.BlackPlayer.Name,
		game.WhitePlayer.Name)
	return &discordgo.MessageEmbed{
		Title:       "Game Started!",
		Description: desc,
		Color:       GreenEmbed,
	}
}

func makeSimulationStartEmbed(game OthelloGame) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       "Simulation started!",
		Description: fmt.Sprintf("Black: %s\n White: %s", game.BlackPlayer.Name, game.WhitePlayer.Name),
		Color:       GreenEmbed,
	}
}

func makeMoveFooter(isBlack bool) string {
	footer := "White to move"
	if isBlack {
		footer = "Black to move"
	}
	return footer
}

func makeGameTitle(game OthelloGame) string {
	return fmt.Sprintf("%s vs %s", game.BlackPlayer.Name, game.WhitePlayer.Name)
}

func makeGameMoveEmbed(game OthelloGame, move Tile, mover Player) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       makeGameTitle(game),
		Description: fmt.Sprintf("%s%s made move: %s", getScoreText(game), mover.Name, move),
		Footer: &discordgo.MessageEmbedFooter{
			Text: makeMoveFooter(game.Board.IsBlackMove),
		},
		Color: GreenEmbed,
	}
}

func makeGameEmbed(game OthelloGame) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       makeGameTitle(game),
		Description: fmt.Sprintf("%s%s to move", getScoreText(game), game.CurrentPlayer().Name),
		Footer: &discordgo.MessageEmbedFooter{
			Text: makeMoveFooter(game.Board.IsBlackMove),
		},
		Color: GreenEmbed,
	}
}

func makeAnalysisEmbed(game OthelloGame, level uint64) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       fmt.Sprintf("Game analysis using engine level %d", level),
		Description: getScoreText(game),
		Footer:      &discordgo.MessageEmbedFooter{Text: "Positive heuristics are better for the player to move, and negative heuristics are worse"},
	}
}

func makeGameOverEmbed(game OthelloGame, result GameResult, statsResult StatsResult, move Tile) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title: "Game has ended",
		Description: fmt.Sprintf("%s%s\n%s",
			getMoveMessage(result.Winner, move.String()),
			getScoreMessage(game.Board.WhiteScore(), game.Board.BlackScore()),
			getStatsMessage(result, statsResult),
		),
	}
}

func makeForfeitEmbed(result GameResult, statsResult StatsResult) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       "Game has ended",
		Description: fmt.Sprintf("%s\n%s", getForfeitMessage(result.Winner), getStatsMessage(result, statsResult)),
		Color:       GreenEmbed,
	}
}

func makeStepEdit(renderer Renderer, step SimStep) *discordgo.WebhookEdit {
	var edit *discordgo.WebhookEdit
	img := renderer.DrawBoardMoves(step.Game.Board, step.Game.Board.FindCurrentMoves())
	if !step.Ok {
		edit = makeEmbedTextEdit("Failed to retrieve simulation data from engine.")
	} else if step.Finished {
		updtEmbed := makeSimulationEndEmbed(step.Game, step.Move)
		edit = makeEmbedEdit(updtEmbed, img)
		edit.Components = &[]discordgo.MessageComponent{}
	} else {
		updtEmbed := makeSimulationEmbed(step.Game, step.Move)
		edit = makeEmbedEdit(updtEmbed, img)
	}
	return edit
}

func makeSimulationEmbed(game OthelloGame, move Tile) *discordgo.MessageEmbed {
	return &discordgo.MessageEmbed{
		Title:       makeGameTitle(game),
		Description: fmt.Sprintf("%s%s has moved: %s", getScoreText(game), game.OtherPlayer().Name, move.String()),
		Footer: &discordgo.MessageEmbedFooter{
			Text: makeMoveFooter(game.Board.IsBlackMove),
		},
		Color: GreenEmbed,
	}
}

func makeSimulationEndEmbed(game OthelloGame, move Tile) *discordgo.MessageEmbed {
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

func makeStatsEmbed(user *discordgo.User, stats Stats) *discordgo.MessageEmbed {
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

func makeLeaderboardEmbed(stats []Stats) *discordgo.MessageEmbed {
	var desc strings.Builder

	if len(stats) == 0 {
		desc.WriteString("No players have registered their stats")
	} else {
		desc.WriteString("```\n")
		for i, stats := range stats {
			desc.WriteString(rightPad(fmt.Sprintf("%d)", i+1), 4))
			desc.WriteString(rightPad(stats.Player.Name, 25))
			desc.WriteString(rightPad(fmt.Sprintf("%.2f", stats.Elo), 25))
			desc.WriteString("\n")
		}
		desc.WriteString("```")
	}

	return &discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       GreenEmbed,
		Footer: &discordgo.MessageEmbedFooter{
			Text: fmt.Sprintf("Top %d rated players", LeaderboardSize),
		},
	}
}

func makeMovesEmbed(game OthelloGame) *discordgo.MessageEmbed {
	var desc strings.Builder

	desc.WriteString("```\n")
	desc.WriteString("Black\t\tWhite\n")

	for i, move := range game.MoveList {
		if i%2 == 0 {
			fmt.Fprintf(&desc, "%d. %s", i/2+1, move)
		} else {
			fmt.Fprintf(&desc, "\t\t%s\n", move)
		}
	}
	desc.WriteString("```")

	return &discordgo.MessageEmbed{
		Title:       makeGameTitle(game),
		Description: desc.String(),
		Color:       GreenEmbed,
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
