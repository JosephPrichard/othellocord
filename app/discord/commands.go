package discord

import (
	"bytes"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
	"image"
	"image/jpeg"
	"log/slog"
	"othellocord/app/othello"
	"strconv"
	"strings"
)

type Handler struct {
	Db *sql.DB
	Uc UserCache
	Cc ChallengeCache
	Gs GameStore
	Rc othello.RenderCache
}

type OptError struct {
	Name         string
	InvalidValue any
}

func (e OptError) Error() string {
	if e.InvalidValue == "" {
		return fmt.Sprintf("expected an option '%s' to be provided", e.Name)
	} else {
		return fmt.Sprintf("option '%s' received invalid value '%v'", e.Name, e.InvalidValue)
	}
}

type SubCmdError struct {
	Name           string
	ExpectedValues []string
}

func (e SubCmdError) Error() string {
	if e.Name == "" {
		return fmt.Sprintf("expected a subcommand with one of following values %v", e.ExpectedValues)
	} else {
		return fmt.Sprintf("invalid subcommand '%s', expected one of following values %v", e.Name, e.ExpectedValues)
	}
}

func (h Handler) HandleCommand(dg *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.WithValue(context.Background(), "trace", uuid.NewString())

	cmd := i.ApplicationCommandData()
	slog.Info("received a command", "name", cmd.Name, "options", cmd.Options)

	var err error

	switch cmd.Name {
	case "challenge":
		err = h.HandleChallenge(ctx, dg, i)
	case "accept":
		err = h.HandleAccept(ctx, dg, i)
	case "forfeit":
		err = h.HandleForfeit(ctx, dg, i)
	case "move":
		err = h.HandleMove(ctx, dg, i)
	case "view":
		err = h.HandleView(ctx, dg, i)
	case "analyze":
		err = h.HandleAnalyze(ctx, dg, i)
	case "simulate":
		err = h.HandleSimulate(ctx, dg, i)
	case "stats":
		err = h.HandleStats(ctx, dg, i)
	case "leaderboard":
		err = h.HandleLeaderboard(ctx, dg, i)
	}
	// if a handler returns an error, it should not have sent an interaction response yet
	if err != nil {
		handleInteractionError(ctx, dg, i, err)
	}
}

var ChallengeSubCmds = []string{"bot", "user"}

func (h Handler) HandleChallenge(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	subCmd := getSubcommand(i)
	switch subCmd {
	case "bot":
		return h.HandleBotChallengeCommand(ctx, dg, i)
	case "user":
		return h.HandleUserChallengeCommand(ctx, dg, i)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func (h Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()

	level, err := h.getIntOpt(cmd, "level", 3, IsValidBotLevel)

	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return UserNotProvided
	}

	game, err := CreateBotGame(ctx, h.Gs, PlayerFromUser(user), level)
	if err != nil {
		return err
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.FindCurrentMoves())

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond embed in HandleBotChallengeCommand", err)
	}
	return nil
}

func (h Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()

	opponent, err := h.getPlayerOpt(ctx, cmd, "opponent")
	if err != nil {
		return err
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return UserNotProvided
	}

	onExpiry := func() {
		if _, err := dg.ChannelMessageSend(i.ChannelID, fmt.Sprintf("%s Challenge timed out!", player.Tag())); err != nil {
			slog.Error("failed to send onExpiry message in HandleUserChallengeCommand", err)
		}
	}
	if err := CreateChallenge(ctx, h.Cc, Challenge{Challenger: player, Challenged: opponent}, onExpiry); err != nil {
		return err
	}

	msg := fmt.Sprintf("%s, %s has challenged you to a game of Othello. Type `/accept` %s, or ignore to decline", opponent, player.Name, player.Tag())

	if err := dg.InteractionRespond(i.Interaction, createStringResponse(msg)); err != nil {
		slog.Error("failed to respond embed in HandleUserChallengeCommand", err)
	}
	return nil
}

func (h Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := PlayerFromUser(i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd, "challenger")
	if err != nil {
		return err
	}

	var game Game
	if err := AcceptChallenge(ctx, h.Cc, Challenge{Challenged: player, Challenger: opponent}); err != nil {
		return err
	}
	if game, err = CreateGame(ctx, h.Gs, opponent, player); err != nil {
		return err
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoard(h.Rc, game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond embed in HandleAccept", err)
	}
	return nil
}

func (h Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

func (h Handler) HandleMove(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

func (h Handler) HandleView(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

func (h Handler) HandleAnalyze(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

func (h Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

var UserNotProvided = errors.New("user not provided")

func (h Handler) HandleStats(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()

	var user *discordgo.User
	var err error

	userOpt := cmd.GetOption("player")
	if userOpt != nil {
		if user, err = FetchUser(ctx, h.Uc, userOpt.Value.(string)); err != nil {
			return err
		}
	} else if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	}
	if user == nil {
		return UserNotProvided
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.Uc, user.ID); err != nil {
		return err
	}

	embed := &discordgo.MessageEmbed{
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
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond embed in HandleStats", err)
	}
	return nil
}

func (h Handler) HandleLeaderboard(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, h.Db, h.Uc); err != nil {
		return err
	}

	var desc strings.Builder
	desc.WriteString("```\n")
	for i, stats := range stats {
		desc.WriteString(rightPad(fmt.Sprintf("%d)", i), 4))
		desc.WriteString(leftPad(stats.Player.Name, 32))
		desc.WriteString(leftPad(fmt.Sprintf("%.2f", stats.Elo), 12))
		desc.WriteString("\n")
	}
	desc.WriteString("```")

	embed := &discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       0x00ff00,
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond embed in HandleLeaderboard", err)
	}
	return nil
}

func (h Handler) getPlayerOpt(ctx context.Context, cmd discordgo.ApplicationCommandInteractionData, name string) (Player, error) {
	challengerOpt := cmd.GetOption(name)
	if challengerOpt == nil {
		return Player{}, OptError{Name: name}
	}
	opponent, err := FetchPlayer(ctx, h.Uc, challengerOpt.Value.(string))
	if err != nil {
		return Player{}, err
	}
	return opponent, nil
}

func (h Handler) getIntOpt(cmd discordgo.ApplicationCommandInteractionData, name string, defaultInt int, isValid func(int) bool) (int, error) {
	level := defaultInt

	levelOpt := cmd.GetOption(name)
	if levelOpt != nil {
		level = levelOpt.Value.(int)
	}
	if isValid(level) {
		return level, nil
	}
	return 0, OptError{Name: name, InvalidValue: level}
}

func createStringResponse(msg string) *discordgo.InteractionResponse {
	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: msg,
		},
	}
}

func createEmbedResponse(embed *discordgo.MessageEmbed, img image.Image) *discordgo.InteractionResponse {
	var files []*discordgo.File

	if img != nil {
		var buf bytes.Buffer
		if err := jpeg.Encode(&buf, img, nil); err != nil {
			// we can't really do anything if this fails, it would be an issue with the board renderer
			slog.Error("failed to encode image", "error", err)
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

	return &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Embeds: []*discordgo.MessageEmbed{embed},
			Files:  files,
		},
	}
}

func getSubcommand(i *discordgo.InteractionCreate) string {
	cmd := i.ApplicationCommandData()
	if len(cmd.Options) > 0 {
		firstOpt := cmd.Options[0]
		if firstOpt.Type == discordgo.ApplicationCommandOptionSubCommand {
			return firstOpt.Name
		}
	}
	return ""
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, err error) {
	trace := ctx.Value("trace")
	slog.Error("error when handling command", "error", err)

	content := "An unexpected error occurred"

	switch err.(type) {
	case *SubCmdError:
		content = err.Error()
	case *OptError:
		content = err.Error()
	}

	resp := &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: content,
		},
	}
	if err = dg.InteractionRespond(i.Interaction, resp); err != nil {
		slog.Error("failed to send message in handleInteractionError", "trace", trace, err)
	}
}
