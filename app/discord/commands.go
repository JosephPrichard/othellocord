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
	subCmd, options := getSubcommand(i)
	switch subCmd {
	case "bot":
		return h.HandleBotChallengeCommand(ctx, dg, i, options)
	case "user":
		return h.HandleUserChallengeCommand(ctx, dg, i, options)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func (h Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	level, err := getNumericOpt(options, "level", 3)
	if err != nil {
		return err
	}
	if !IsValidBotLevel(level) {
		return OptError{Name: "level", InvalidValue: level}
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return UserNotProvided
	}

	game, err := h.Gs.CreateBotGame(ctx, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a game.")); err != nil {
			slog.Error("failed to respond embed in handle bot challenge command", err)
		}
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to create bot game with level=%d, player=%v cmd: %v", level, player, err)
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.FindCurrentMoves())

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := h.getPlayerOpt(ctx, options, "opponent")
	if err != nil {
		return fmt.Errorf("failed to get plater opt: %v", err)
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return UserNotProvided
	}

	expireChan := make(chan struct{}, 1)
	h.Cc.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, expireChan)

	go func() {
		_, ok := <-expireChan
		if ok {
			if _, err := dg.ChannelMessageSend(i.ChannelID, fmt.Sprintf("<@%s> Challenge timed out!", player.Id)); err != nil {
				slog.Error("failed to send expiration message in handle user challenge command", err)
			}
		}
	}()
	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.Id, player.Name, player.Id)
	_ = dg.InteractionRespond(i.Interaction, createStringResponse(msg))

	return nil
}

var ErrUnknownChallenge = errors.New("attempted to accept an invalid or unknown challenge")

func (h Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := PlayerFromUser(i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %v", err)
	}

	didAccept := h.Cc.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		return ErrUnknownChallenge
	}
	game, err := h.Gs.CreateGame(ctx, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create game with opponent=%v cmd: %v", opponent, err)
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoard(h.Rc, game.Board)

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h Handler) HandleView(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return UserNotProvided
	}

	game, err := h.Gs.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently in a game."))
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to get game for player=%s: %v", user.ID, err)
	}

	embed := CreateGameEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.FindCurrentMoves())

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h Handler) HandleMove(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	return nil
}

func (h Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return UserNotProvided
	}

	game, err := h.Gs.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently in a game."))
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to get game for player=%s: %v", user.ID, err)
	}
	h.Gs.DeleteGame(game)
	gr := game.CreateForfeitResult(user.ID)
	sr, err := UpdateStats(ctx, h.Db, gr)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %v", user.ID, err)
	}

	embed := CreateForfeitEmbed(gr, sr)
	img := othello.DrawBoard(h.Rc, game.Board)

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img))
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
		if user, err = h.Uc.FetchUser(ctx, userOpt.Value.(string)); err != nil {
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
		Color: GreenColor,
	}

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil))
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
		desc.WriteString(rightPad(fmt.Sprintf("%d)", i+1), 4))
		desc.WriteString(leftPad(stats.Player.Name, 32))
		desc.WriteString(leftPad(fmt.Sprintf("%.2f", stats.Elo), 12))
		desc.WriteString("\n")
	}
	desc.WriteString("```")

	embed := &discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       GreenColor,
	}

	_ = dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil))
	return nil
}

func getSubcommand(i *discordgo.InteractionCreate) (string, []*discordgo.ApplicationCommandInteractionDataOption) {
	cmd := i.ApplicationCommandData()
	if len(cmd.Options) > 0 {
		firstOpt := cmd.Options[0]
		if firstOpt.Type == discordgo.ApplicationCommandOptionSubCommand {
			return firstOpt.Name, firstOpt.Options
		}
	}
	return "", nil
}

func (h Handler) getPlayerOpt(ctx context.Context, options []*discordgo.ApplicationCommandInteractionDataOption, name string) (Player, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		opponent, err := h.Uc.FetchPlayer(ctx, opt.Value.(string))
		if err != nil {
			return Player{}, fmt.Errorf("failed to get player option name=%s, err: %v", name, err)
		}
		return opponent, nil
	}
	return Player{}, OptError{Name: name}
}

func getNumericOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string, defaultInt int) (int, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		value, ok := opt.Value.(float64)
		if !ok {
			return defaultInt, OptError{Name: name, InvalidValue: opt.Value}
		}
		return int(value), nil
	}
	return defaultInt, nil
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

var sentinelMap = map[string]string{
	ErrUnknownChallenge.Error(): "Challenge against this player does not exist.",
	ErrAlreadyPlaying.Error():   "One or more players in this challenge are already in a game.",
	ErrUnknownChallenge.Error(): "Challenge from this player does not exist.",
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, err error) {
	trace := ctx.Value("trace")
	slog.Error("error when handling command", "trace", trace, "error", err)

	content := "An unexpected error occurred"

	errMsg, ok := sentinelMap[err.Error()]
	if ok {
		content = errMsg
	} else {
		switch err.(type) {
		case *SubCmdError:
			content = err.Error()
		case *OptError:
			content = err.Error()
		}
	}

	resp := &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: content,
		},
	}
	_ = dg.InteractionRespond(i.Interaction, resp)
}
