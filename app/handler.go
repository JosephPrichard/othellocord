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

func (h *Handler) HandeInteractionCreate(dg *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.WithValue(context.Background(), TraceKey, uuid.NewString())

	var err error

	switch i.Type {
	case discordgo.InteractionApplicationCommandAutocomplete:
		fallthrough
	case discordgo.InteractionApplicationCommand:
		cmd := i.ApplicationCommandData()
		slog.Info("received a command", "name", cmd.Name, "options", formatOptions(cmd.Options))

		switch cmd.Name {
		case "challenge":
			err = h.HandleChallenge(ctx, dg, i)
		case "accept":
			err = h.HandleAccept(ctx, dg, i)
		case "forfeit":
			err = h.HandleForfeit(ctx, dg, i)
		case "move":
			if i.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				h.HandleMoveAutocomplete(ctx, dg, i)
			} else {
				err = h.HandleMove(ctx, dg, i)
			}
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
	case discordgo.InteractionMessageComponent:
		msg := i.MessageComponentData()
		slog.Info("received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			err = h.HandlePauseComponent(dg, i, key)
		case SimStopKey:
			err = h.HandleStopComponent(dg, i, key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
		// if a handler returns an error, it should not have sent an interaction response yet
		if err != nil {
			handleInteractionError(ctx, dg, i, err)
		}
	}
}

var ChallengeSubCmds = []string{"service", "user"}

func (h *Handler) HandleChallenge(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	subCmd, options := getSubcommand(i)
	switch subCmd {
	case "service":
		return h.HandleBotChallengeCommand(ctx, dg, i, options)
	case "user":
		return h.HandleUserChallengeCommand(ctx, dg, i, options)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func (h *Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		return err
	}

	var player Player
	if i.Interaction.Member != nil {
		player = MakeHumanPlayer(*i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := CreateBotGame(ctx, h.Db, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		interactionRespond(dg, i.Interaction, createStringResponse("You're already in a OthelloGame."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create service OthelloGame with level=%d, player=%v cmd: %s", level, player, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := h.getPlayerOpt(ctx, options, "opponent")
	if err != nil {
		return fmt.Errorf("failed to get plater opt: %s", err)
	}

	var player Player
	if i.Interaction.Member != nil {
		player = MakeHumanPlayer(*i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	channelID := i.ChannelID
	handleExpire := func() {
		if _, err = dg.ChannelMessageSend(channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID)); err != nil {
			slog.Error("failed to send user challenge expire", "err", err)
		}
	}
	h.ChallengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a Game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.ID, player.Name, player.ID)

	interactionRespond(dg, i.Interaction, createStringResponse(msg))
	return nil
}

func (h *Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := MakeHumanPlayer(*i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %s", err)
	}

	didAccept := h.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		interactionRespond(dg, i.Interaction, createStringResponse("Cannot accept a challenge that does not exist."))
		return nil
	}
	game, err := CreateGame(ctx, h.Db, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create OthelloGame with opponent=%v cmd: %s", opponent, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoard(game.Board)

	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleView(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, i.Interaction, createStringResponse("You're not playing a OthelloGame."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get OthelloGame for player=%s: %s", user.ID, err)
	}

	embed := createGameEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, i.Interaction, createStringResponse("You're not currently in a game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get game for player=%s: %s", user.ID, err)
	}
	if err := DeleteGame(ctx, h.Db, game); err != nil {
		return fmt.Errorf("failed to delete game in forfeit: %s", err)
	}

	gameResult := game.CreateForfeitResult(user.ID)
	statsResult, err := UpdateStats(ctx, h.Db, gameResult)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %s", user.ID, err)
	}

	embed := createForfeitEmbed(gameResult, statsResult)
	img := h.Renderer.DrawBoard(game.Board)

	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleMoveAutocomplete(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) {
	var moves []Tile
	if i.Interaction.Member != nil {
		if game, err := GetGame(ctx, h.Db, i.Interaction.Member.User.ID); err == nil {
			moves = game.Board.FindCurrentMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	interactionRespond(dg, i.Interaction, createAutocompleteResponse(choices))
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

func (h *Handler) HandleMove(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	move, moveStr, err := getTileOpt(i.ApplicationCommandData().Options, "Move")
	if err != nil {
		return err
	}
	var player Player
	if i.Interaction.Member != nil {
		player = MakeHumanPlayer(*i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := MakeMoveValidated(ctx, h.Db, player.ID, move)

	if resp := createMoveErrorResp(err, moveStr); resp != nil {
		interactionRespond(dg, i.Interaction, resp)
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to make move=%v for player=%s: %s", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			return fmt.Errorf("failed to handle game over while handling moves: %s", err)
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

	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, img))
	return nil
}

func (h *Handler) HandleAnalyze(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	trace := ctx.Value(TraceKey)

	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Minute*2))
	defer cancel()

	level, err := getLevelOpt(i.ApplicationCommandData().Options, "level")
	if err != nil {
		return err
	}
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		interactionRespond(dg, i.Interaction, createStringResponse("You're not currently in a OthelloGame."))
		return nil
	}

	interactionRespond(dg, i.Interaction, createStringResponse("Analyzing... Wait a second..."))

	respCh := h.Sh.FindRankedMoves(game, LevelToDepth(level))
	select {
	case resp := <-respCh:
		if resp.ok {
			embed := createAnalysisEmbed(game, level)
			img := h.Renderer.DrawBoardAnalysis(game.Board, resp.moves)
			interactionResponseEdit(dg, i.Interaction, createEmbedEdit(embed, img))
		} else {
			interactionResponseEdit(dg, i.Interaction, createEmbedTextEdit("Failed to retrieve analysis data from engine."))
		}
	case <-ctx.Done():
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		interactionResponseEdit(dg, i.Interaction, createStringEdit("Timed out while waiting for a response."))
	}
	return nil
}

func (h *Handler) makeSendSimulate(dg *discordgo.Session, i *discordgo.InteractionCreate) func(msg SimPanel) {
	return func(panel SimPanel) {
		var edit *discordgo.WebhookEdit
		img := h.Renderer.DrawBoardMoves(panel.Game.Board, panel.Game.Board.FindCurrentMoves())

		if !panel.Ok {
			edit = createEmbedTextEdit("Failed to retrieve simulation data from engine.")
		} else if panel.Finished {
			updtEmbed := createSimulationEndEmbed(panel.Game, panel.Move)
			edit = createEmbedEdit(updtEmbed, img)
			edit.Components = &[]discordgo.MessageComponent{}
		} else {
			updtEmbed := createSimulationEmbed(panel.Game, panel.Move)
			edit = createEmbedEdit(updtEmbed, img)
		}

		interactionResponseEdit(dg, i.Interaction, edit)
	}
}

func (h *Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Hour*1)) // a simulation can stay paused for up to an hour
	defer cancel()

	cmd := i.ApplicationCommandData()

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
	interactionRespond(dg, i.Interaction, response)

	state := &SimState{StopChan: make(chan struct{})}
	simChan := make(chan SimPanel, SimCount)

	h.SimCache.Set(simulationID, state, SimulationTtl)

	go BeginSimulate(ctx, BeginSimInput{
		Sh:          h.Sh,
		InitialGame: initialGame,
		SimChan:     simChan,
	})

	handleCancel := func() {
		cancel()
		edit := discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}}
		interactionResponseEdit(dg, i.Interaction, &edit)
	}
	handleSend := h.makeSendSimulate(dg, i)

	ReceiveSimulate(ctx, RecvSimInput{
		State:        state,
		RecvChan:     simChan,
		HandleCancel: handleCancel,
		HandleSend:   handleSend,
		Delay:        delay,
	})
	return nil
}

func (h *Handler) HandleStats(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user discordgo.User
	var err error

	userOpt := i.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		if user, err = h.UserCache.GetUser(ctx, userOpt.Value.(string)); err != nil {
			return fmt.Errorf("failed to get user while handling stats: %s", err)
		}
	} else if i.Interaction.Member != nil {
		user = *i.Interaction.Member.User
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.UserCache, user.ID); err != nil {
		return fmt.Errorf("failed to read stats while handling stats: %s", err)
	}

	embed := createStatsEmbed(user, stats)
	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, nil))
	return nil
}

const LeaderboardSize = 50

func (h *Handler) HandleLeaderboard(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, h.Db, h.UserCache, LeaderboardSize); err != nil {
		return fmt.Errorf("failed to top stats while handling leaderboard: %s", err)
	}

	embed := createLeaderboardEmbed(stats)
	interactionRespond(dg, i.Interaction, createEmbedResponse(embed, nil))
	return nil
}

func (h *Handler) HandlePauseComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.SimCache.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	simulationID = item.Key()
	state := item.Value()

	isPaused := state.IsPaused.Toggle()
	isPaused = !isPaused // we need to reapply the operation because toggle returns the old value

	interactionRespond(dg, i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})

	components := createSimulationActionRow(simulationID, isPaused)
	interactionResponseEdit(dg, i.Interaction, &discordgo.WebhookEdit{Components: &components})
	return nil
}

func (h *Handler) HandleStopComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.SimCache.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	state := item.Value()
	state.StopChan <- struct{}{}

	interactionRespond(dg, i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
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

func handleInteractionError(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, err error) {
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
	if err := dg.InteractionRespond(i.Interaction, resp); err != nil {
		slog.Error("failed to respond interaction error", "err", err)
	}
}
