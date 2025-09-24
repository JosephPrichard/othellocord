package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"image"
	"log/slog"
	"time"

	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
)

type Handler struct {
	Db             *sql.DB
	Sh             *NTestShell
	Renderer       Renderer
	UserCache      UserCache
	ChallengeCache ChallengeCache
	SimCache       SimCache
}

var ErrUserNotProvided = errors.New("user not provided")

func (h *Handler) HandeInteractionCreate(dg *discordgo.Session, ic *discordgo.InteractionCreate) {
	ctx := context.WithValue(context.Background(), TraceKey, uuid.NewString())

	var err error

	switch ic.Type {
	case discordgo.InteractionApplicationCommandAutocomplete:
		fallthrough
	case discordgo.InteractionApplicationCommand:
		cmd := ic.ApplicationCommandData()
		slog.Info("received a command", "name", cmd.Name, "options", formatOptions(cmd.Options))

		switch cmd.Name {
		case "challenge":
			err = h.HandleChallenge(ctx, dg, ic)
		case "accept":
			err = h.HandleAccept(ctx, dg, ic)
		case "forfeit":
			err = h.HandleForfeit(ctx, dg, ic)
		case "move":
			if ic.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				h.HandleMoveAutocomplete(ctx, dg, ic)
			} else {
				err = h.HandleMove(ctx, dg, ic)
			}
		case "view":
			err = h.HandleView(ctx, dg, ic)
		case "analyze":
			err = h.HandleAnalyze(ctx, dg, ic)
		case "simulate":
			err = h.HandleSimulate(ctx, dg, ic)
		case "stats":
			err = h.HandleStats(ctx, dg, ic)
		case "leaderboard":
			err = h.HandleLeaderboard(ctx, dg, ic)
		}
		// if a handler returns an error, it should not have sent an interaction response yet
		if err != nil {
			handleInteractionError(ctx, dg, ic, err)
		}
	case discordgo.InteractionMessageComponent:
		msg := ic.MessageComponentData()
		slog.Info("received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			err = h.HandlePauseComponent(dg, ic, key)
		case SimStopKey:
			err = h.HandleStopComponent(dg, ic, key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
		// if a handler returns an error, it should not have sent an interaction response yet
		if err != nil {
			handleInteractionError(ctx, dg, ic, err)
		}
	}
}

var ChallengeSubCmds = []string{"bot", "user"}

func (h *Handler) HandleChallenge(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	subCmd, options := getSubcommand(ic)
	switch subCmd {
	case "bot":
		return h.HandleBotChallengeCommand(ctx, dg, ic, options)
	case "user":
		return h.HandleUserChallengeCommand(ctx, dg, ic, options)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func (h *Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		return err
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := CreateBotGame(ctx, h.Db, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		interactionRespond(dg, ic.Interaction, createStringResponse("You're already in a game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create game with level=%d, player=%v cmd: %s", level, player, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := h.getPlayerOpt(ctx, options, "opponent")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %s", err)
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	channelID := ic.ChannelID
	handleExpire := func() {
		if _, err = dg.ChannelMessageSend(channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID)); err != nil {
			slog.Error("failed to send user challenge expire", "err", err)
		}
	}
	h.ChallengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.ID, player.Name, player.ID)

	interactionRespond(dg, ic.Interaction, createStringResponse(msg))
	return nil
}

func (h *Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	cmd := ic.ApplicationCommandData()
	player := MakeHumanPlayer(ic.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %s", err)
	}

	didAccept := h.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		interactionRespond(dg, ic.Interaction, createStringResponse("Cannot accept a challenge that does not exist."))
		return nil
	}
	game, err := CreateGame(ctx, h.Db, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create OthelloGame with opponent=%v cmd: %s", opponent, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoard(game.Board)

	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleView(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, ic.Interaction, createStringResponse("You're not playing a OthelloGame."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get OthelloGame for player=%s: %s", user.ID, err)
	}

	embed := createGameEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, ic.Interaction, createStringResponse("You're not currently in a Game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %s", user.ID, err)
	}
	if err := DeleteGame(ctx, h.Db, game); err != nil {
		return fmt.Errorf("failed to delete Game in forfeit: %s", err)
	}

	gameResult := game.CreateForfeitResult(user.ID)
	statsResult, err := UpdateStats(ctx, h.Db, gameResult)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %s", user.ID, err)
	}

	embed := createForfeitEmbed(gameResult, statsResult)
	img := h.Renderer.DrawBoard(game.Board)

	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleMoveAutocomplete(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) {
	var moves []Tile
	if ic.Interaction.Member != nil {
		if game, err := GetGame(ctx, h.Db, ic.Interaction.Member.User.ID); err == nil {
			moves = game.Board.FindCurrentMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	interactionRespond(dg, ic.Interaction, createAutocompleteResponse(choices))
}

func (h *Handler) handleGameOver(ctx context.Context, game OthelloGame, move Tile) (*discordgo.MessageEmbed, image.Image, error) {
	var gameResult GameResult
	var statsResult StatsResult

	gameResult = game.CreateResult()
	statsResult, err := UpdateStats(ctx, h.Db, gameResult)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to update stats for result=%v: %s", gameResult, err)
	}

	embed := createGameOverEmbed(game, gameResult, statsResult, move)
	img := h.Renderer.DrawBoard(game.Board)
	return embed, img, nil
}

func (h *Handler) HandleMove(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	move, moveStr, err := getTileOpt(ic.ApplicationCommandData().Options, "move")
	if err != nil {
		return err
	}
	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := MakeMoveValidated(ctx, h.Db, player.ID, move)

	if resp := createMoveErrorResp(err, moveStr); resp != nil {
		interactionRespond(dg, ic.Interaction, resp)
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to make move=%v for player=%s: %s", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			return fmt.Errorf("failed to handle Game over while handling Moves: %s", err)
		}
	} else {
		img = h.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		if game.CurrentPlayer().IsHuman() {
			embed = createGameMoveEmbed(game, move)
		} else {
			embed = createGameEmbed(game)
			// TODO send bot move to queue here
		}
	}

	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleAnalyze(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	trace := ctx.Value(TraceKey)

	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Minute*2))
	defer cancel()

	level, err := getLevelOpt(ic.ApplicationCommandData().Options, "level")
	if err != nil {
		return err
	}
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, ic.Interaction, createStringResponse("You're not currently in a OthelloGame."))
		return nil
	}

	interactionRespond(dg, ic.Interaction, createStringResponse("Analyzing... Wait a second..."))

	respCh := h.Sh.FindRankedMoves(game, LevelToDepth(level))
	select {
	case resp := <-respCh:
		if resp.Ok {
			embed := createAnalysisEmbed(game, level)
			img := h.Renderer.DrawBoardAnalysis(game.Board, resp.Moves)
			interactionResponseEdit(dg, ic.Interaction, createEmbedEdit(embed, img))
		} else {
			interactionResponseEdit(dg, ic.Interaction, createEmbedTextEdit("Failed to retrieve analysis data from engine."))
		}
	case <-ctx.Done():
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		interactionResponseEdit(dg, ic.Interaction, createStringEdit("Timed out while waiting for a response."))
	}
	return nil
}

func (h *Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Hour*1)) // a simulation can stay paused for up to an hour
	defer cancel()

	// extract simulation inputs and send the initial simulation response
	cmd := ic.ApplicationCommandData()

	var whiteLevel uint64
	var blackLevel uint64
	var delay time.Duration
	var err error

	if whiteLevel, err = getLevelOpt(cmd.Options, "white-level"); err != nil {
		return err
	}
	if blackLevel, err = getLevelOpt(cmd.Options, "black-level"); err != nil {
		return err
	}
	if delay, err = getDelayOpt(cmd.Options, "delay"); err != nil {
		return err
	}

	initialGame := OthelloGame{
		WhitePlayer: MakeBotPlayer(whiteLevel),
		BlackPlayer: MakeBotPlayer(blackLevel),
		Board:       InitialBoard(),
	}
	embed := createSimulationStartEmbed(initialGame)
	img := h.Renderer.DrawBoard(initialGame.Board)

	simulationID := uuid.New().String()

	response := createComponentResponse(embed, img, createSimulationActionRow(simulationID, false))
	interactionRespond(dg, ic.Interaction, response)

	// run the simulation against the engine and add it to the cache (so it can be paused/resumed)
	state := &SimState{Cancel: cancel}
	simChan := make(chan SimStep, MaxSimCount) // give this a size so we don't block on send

	h.SimCache.Set(simulationID, state, SimulationTtl)

	go GenerateSimulation(ctx, h.Sh, initialGame, simChan)
	h.RecvSimulation(ctx, dg, ic, delay, state, simChan)

	return nil
}

func (h *Handler) RecvSimulation(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, delay time.Duration, state *SimState, simChan chan SimStep) {
	trace := ctx.Value(TraceKey)

	ticker := time.NewTicker(delay)
	for {
		select {
		case <-ctx.Done():
			slog.Info("simulation receiver stopped", "trace", trace)
			interactionResponseEdit(dg, ic.Interaction, &discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}})
			return
		case <-ticker.C:
			if state.IsPaused.Load() { // paused? check again once the ticker executes
				continue
			}
			step, ok := <-simChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return
			}
			interactionResponseEdit(dg, ic.Interaction, createStepEdit(h.Renderer, step))
		}
	}
}

func (h *Handler) HandleStats(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	var user discordgo.User
	var err error

	userOpt := ic.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		if user, err = h.UserCache.GetUser(ctx, userOpt.Value.(string)); err != nil {
			return fmt.Errorf("failed to get user while handling stats: %s", err)
		}
	} else if ic.Interaction.Member != nil {
		user = *ic.Interaction.Member.User
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.UserCache, user.ID); err != nil {
		return fmt.Errorf("failed to read stats while handling stats: %s", err)
	}

	embed := createStatsEmbed(user, stats)
	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, nil))
	return nil
}

const LeaderboardSize = 50

func (h *Handler) HandleLeaderboard(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, h.Db, h.UserCache, LeaderboardSize); err != nil {
		return fmt.Errorf("failed to top stats while handling leaderboard: %s", err)
	}

	embed := createLeaderboardEmbed(stats)
	interactionRespond(dg, ic.Interaction, createEmbedResponse(embed, nil))
	return nil
}

func (h *Handler) HandlePauseComponent(dg *discordgo.Session, ic *discordgo.InteractionCreate, simulationID string) error {
	acknowledge := func() {
		interactionRespond(dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := h.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return nil
	}

	simulationID = item.Key()
	state := item.Value()

	isPaused := !state.IsPaused.Toggle() // negate this because it returns the old value

	acknowledge()

	components := createSimulationActionRow(simulationID, isPaused)
	interactionResponseEdit(dg, ic.Interaction, &discordgo.WebhookEdit{Components: &components})
	return nil
}

func (h *Handler) HandleStopComponent(dg *discordgo.Session, ic *discordgo.InteractionCreate, simulationID string) error {
	acknowledge := func() {
		interactionRespond(dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := h.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return nil
	}

	state := item.Value()
	state.Cancel()

	acknowledge()
	return nil
}

func channelMessageSendComplex(dg *discordgo.Session, channelID string, data *discordgo.MessageSend) {
	if _, err := dg.ChannelMessageSendComplex(channelID, data); err != nil {
		slog.Error("failed to send message complex", "err", err)
	}
}

func interactionRespond(dg *discordgo.Session, i *discordgo.Interaction, r *discordgo.InteractionResponse) {
	if err := dg.InteractionRespond(i, r); err != nil {
		slog.Error("failed to send interaction response", "err", err)
	}
}

func interactionResponseEdit(dg *discordgo.Session, i *discordgo.Interaction, e *discordgo.WebhookEdit) {
	if _, err := dg.InteractionResponseEdit(i, e); err != nil {
		slog.Error("failed to send interaction response edit", "err", err)
	}
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, err error) {
	trace := ctx.Value(TraceKey)
	slog.Error("error when handling command", "trace", trace, "err", err)

	content := "An unexpected error occurred"

	switch err.(type) {
	case *SubCmdError, *OptionError:
		content = err.Error()
	}

	resp := &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: content,
		},
	}
	if err := dg.InteractionRespond(ic.Interaction, resp); err != nil {
		slog.Error("failed to respond interaction error", "err", err)
	}
}
