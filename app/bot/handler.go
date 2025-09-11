package bot

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"image"
	"log/slog"
	"othellocord/app/othello"
	"time"

	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
)

type Handler struct {
	Db              *sql.DB
	Renderer        othello.Renderer
	Wq              chan WorkerRequest
	UserCache       UserCache
	ChallengeCache  ChallengeCache
	GameCache       GameCache
	SimulationCache SimCache
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

var ChallengeSubCmds = []string{"bot", "user"}

func (h *Handler) HandleChallenge(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
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

func (h *Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		return err
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(*i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := h.GameCache.CreateBotGame(ctx, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a Game.")); err != nil {
			slog.Error("failed to respond to already playing Game for bot challenge", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create bot Game with level=%d, player=%v cmd: %w", level, player, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.LoadPotentialMoves())

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to bot challenge", "err", err)
	}
	return nil
}

func (h *Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := h.getPlayerOpt(ctx, options, "opponent")
	if err != nil {
		return fmt.Errorf("failed to get plater opt: %w", err)
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(*i.Interaction.Member.User)
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

	if err := dg.InteractionRespond(i.Interaction, createStringResponse(msg)); err != nil {
		slog.Error("failed to respond to user challenge", "err", err)
	}
	return nil
}

func (h *Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := PlayerFromUser(*i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %w", err)
	}

	didAccept := h.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("Cannot accept a challenge that does not exist.")); err != nil {
			slog.Error("failed to respond to denied accept", "err", err)
		}
		return nil
	}
	game, err := h.GameCache.CreateGame(ctx, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create Game with opponent=%v cmd: %w", opponent, err)
	}

	embed := createGameStartEmbed(game)
	img := h.Renderer.DrawBoard(game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to accept", "err", err)
	}
	return nil
}

func (h *Handler) HandleView(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := h.GameCache.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're not playing a Game.")); err != nil {
			slog.Error("failed to respond to not found view", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %w", user.ID, err)
	}

	embed := createGameEmbed(game)
	img := h.Renderer.DrawBoardMoves(game.Board, game.LoadPotentialMoves())

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to view", "err", err)
	}
	return nil
}

func (h *Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := h.GameCache.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a Game.")); err != nil {
			slog.Error("failed to respond to forfeit game not found", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %w", user.ID, err)
	}
	h.GameCache.DeleteGame(game)

	gameResult := game.CreateForfeitResult(user.ID)
	statsResult, err := UpdateStats(ctx, h.Db, gameResult)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %w", user.ID, err)
	}

	embed := createForfeitEmbed(gameResult, statsResult)
	img := h.Renderer.DrawBoard(game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to forfeit", "err", err)
	}
	return nil
}

func (h *Handler) HandleMoveAutocomplete(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) {
	var moves []othello.Tile
	if i.Interaction.Member != nil {
		if game, err := h.GameCache.GetGame(ctx, i.Interaction.Member.User.ID); err == nil {
			moves = game.LoadPotentialMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	if err := dg.InteractionRespond(i.Interaction, createAutocompleteResponse(choices)); err != nil {
		slog.Error("failed to respond to Move autocomplete", "err", err)
	}
}

func (h *Handler) handleGameOver(ctx context.Context, game Game, move othello.Tile) (*discordgo.MessageEmbed, image.Image, error) {
	var gameResult GameResult
	var statsResult StatsResult

	gameResult = game.CreateResult()
	statsResult, err := UpdateStats(ctx, h.Db, gameResult)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to update stats for result=%v: %w", gameResult, err)
	}

	embed := createGameOverEmbed(game, gameResult, statsResult, move)
	img := h.Renderer.DrawBoard(game.Board)
	return embed, img, nil
}

func (h *Handler) handleBotMove(ctx context.Context, dg *discordgo.Session, channelID string, game Game) {
	request := WorkerRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(GetBotLevel(game.CurrentPlayer().ID)),
		Kind:     GetMoveRequestKind,
		RespChan: make(chan []othello.RankTile, 1),
	}
	h.Wq <- request

	moves := <-request.RespChan
	if len(moves) != 1 {
		panic("expected exactly engine to respond with one move") // assert that the bot can make a move
	}
	move := moves[0].Tile

	game = h.GameCache.MakeMoveUnchecked(game.OtherPlayer().ID, move) // Game will be stored at the ID of the player that is NOT the bot

	var embed *discordgo.MessageEmbed
	var img image.Image
	var err error

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			slog.Error("failed to send game over bot move", "err", err)
		}
	} else {
		embed = createGameMoveEmbed(game, move)
		img = h.Renderer.DrawBoardMoves(game.Board, game.LoadPotentialMoves())
	}
	if _, err := dg.ChannelMessageSendComplex(channelID, createEmbedSend(embed, img)); err != nil {
		slog.Error("failed to send bot move", "err", err)
	}

	if !game.IsGameOver() && game.CurrentPlayer().isBot() {
		h.handleBotMove(ctx, dg, channelID, game)
	}
}

func (h *Handler) HandleMove(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	move, moveStr, err := getTileOpt(i.ApplicationCommandData().Options, "Move")
	if err != nil {
		return err
	}
	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(*i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := h.GameCache.MakeMoveValidated(player.ID, move)

	if resp := createMoveErrorResp(err, moveStr); resp != nil {
		if err := dg.InteractionRespond(i.Interaction, resp); err != nil {
			slog.Error("failed to respond to move command", "err", err)
		}
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to make move=%v for player=%s: %w", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			return fmt.Errorf("failed to handle game over while handling moves: %w", err)
		}
	} else {
		isBot := game.CurrentPlayer().isBot()
		if isBot {
			embed = createGameEmbed(game)
			go h.handleBotMove(ctx, dg, i.ChannelID, game)
		} else {
			embed = createGameMoveEmbed(game, move)
		}
		img = h.Renderer.DrawBoardMoves(game.Board, game.LoadPotentialMoves())
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to move", "err", err)
	}
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

	game, err := h.GameCache.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently in a Game."))
		return nil
	}

	if err := dg.InteractionRespond(i.Interaction, createStringResponse("Analyzing... Wait a second...")); err != nil {
		slog.Error("failed to respond to analyze", "err", err)
	}

	request := WorkerRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(level),
		Kind:     GetMovesRequestKind,
		RespChan: make(chan []othello.RankTile, 1),
	}
	h.Wq <- request

	select {
	case resp := <-request.RespChan:
		embed := createAnalysisEmbed(game, level)
		img := h.Renderer.DrawBoardAnalysis(game.Board, resp)

		if _, err := dg.InteractionResponseEdit(i.Interaction, createEmbedEdit(embed, img)); err != nil {
			slog.Error("failed to edit analyze", "err", err)
		}
	case <-ctx.Done():
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		if _, err := dg.InteractionResponseEdit(i.Interaction, createStringEdit("Timed out while waiting for a response.")); err != nil {
			slog.Error("failed to edit analyze", "err", err)
		}
	}
	return nil
}

func makeCancelSimulation(dg *discordgo.Session, i *discordgo.InteractionCreate) func() {
	return func() {
		edit := discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}}
		if _, err := dg.InteractionResponseEdit(i.Interaction, &edit); err != nil {
			slog.Error("failed to edit message simulate", "err", err)
		}
	}
}

func (h *Handler) makeSendSimulate(dg *discordgo.Session, i *discordgo.InteractionCreate) func(msg SimPanel) {
	return func(panel SimPanel) {
		var edit *discordgo.WebhookEdit
		img := h.Renderer.DrawBoardMoves(panel.Game.Board, panel.Game.LoadPotentialMoves())
		if panel.Finished {
			updtEmbed := createSimulationEndEmbed(panel.Game, panel.Move)
			edit = createEmbedEdit(updtEmbed, img)
			edit.Components = &[]discordgo.MessageComponent{}
		} else {
			updtEmbed := createSimulationEmbed(panel.Game, panel.Move)
			edit = createEmbedEdit(updtEmbed, img)
		}
		if _, err := dg.InteractionResponseEdit(i.Interaction, edit); err != nil {
			slog.Error("failed to edit message simulate", "err", err)
		}
	}
}

func (h *Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Hour*1)) // a simulation can stay paused for up to an hour
	defer cancel()

	trace := ctx.Value(TraceKey)
	cmd := i.ApplicationCommandData()

	var whiteLevel int
	var blackLevel int
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

	initialGame := Game{
		WhitePlayer: PlayerFromLevel(whiteLevel),
		BlackPlayer: PlayerFromLevel(blackLevel),
		Board:       othello.InitialBoard(),
	}
	embed := createSimulationStartEmbed(initialGame)
	img := h.Renderer.DrawBoard(initialGame.Board)

	simulationID := uuid.New().String()

	response := createComponentResponse(embed, img, createSimulationActionRow(simulationID, false))
	if err := dg.InteractionRespond(i.Interaction, response); err != nil {
		slog.Error("failed to respond to simulate", "err", err)
		return nil
	}

	state := &SimState{StopChan: make(chan struct{})}
	simChan := make(chan SimPanel, SimCount)

	h.SimulationCache.Set(simulationID, state, SimulationTtl)

	go BeginSimulate(ctx, BeginSimInput{
		Wq:          h.Wq,
		InitialGame: initialGame,
		SimChan:     simChan,
	})

	slog.Info("simulation receiver started", "delay", delay, "trace", trace)

	handleCancel := makeCancelSimulation(dg, i)
	handleSend := h.makeSendSimulate(dg, i)

	ReceiveSimulate(ctx, RecvSimInput{
		State:        state,
		RecvChan:     simChan,
		DoCancel:     cancel,
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
			return fmt.Errorf("failed to get user while handling stats: %w", err)
		}
	} else if i.Interaction.Member != nil {
		user = *i.Interaction.Member.User
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.UserCache, user.ID); err != nil {
		return fmt.Errorf("failed to read stats while handling stats: %w", err)
	}

	embed := createStatsEmbed(user, stats)
	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond to stats", "err", err)
	}
	return nil
}

const LeaderboardSize = 50

func (h *Handler) HandleLeaderboard(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, h.Db, h.UserCache, LeaderboardSize); err != nil {
		return fmt.Errorf("failed to top stats while handling leaderboard: %w", err)
	}

	embed := createLeaderboardEmbed(stats)
	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond to leaderboard", "err", err)
	}
	return nil
}

func (h *Handler) HandlePauseComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.SimulationCache.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	simulationID = item.Key()
	state := item.Value()

	isPaused := state.IsPaused.Toggle()
	isPaused = !isPaused // we need to reapply the operation because toggle returns the old value

	if err := dg.InteractionRespond(i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}); err != nil {
		slog.Error("failed to follow up with pause message", "err", err)
	}

	components := createSimulationActionRow(simulationID, isPaused)
	if _, err := dg.InteractionResponseEdit(i.Interaction, &discordgo.WebhookEdit{Components: &components}); err != nil {
		slog.Error("failed to edit pause component", "err", err)
	}
	return nil
}

func (h *Handler) HandleStopComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.SimulationCache.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	state := item.Value()
	state.StopChan <- struct{}{}

	if err := dg.InteractionRespond(i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}); err != nil {
		slog.Error("failed to follow up with stop message", "err", err)
	}
	return nil
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
