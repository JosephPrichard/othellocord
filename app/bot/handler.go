package bot

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
	"image"
	"log/slog"
	"othellocord/app/othello"
	"time"
)

type Handler struct {
	Db *sql.DB
	Rc othello.RenderCache
	Wq chan WorkerRequest
	Uc UserCache
	Cc *ChallengeCache
	Gc *GameCache
	Ss *SimCache
}

var ErrUserNotProvided = errors.New("user not provided")

func (h *Handler) HandeInteractionCreate(dg *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.WithValue(context.Background(), "trace", uuid.NewString())

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
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := CreateBotGame(ctx, h.Gc, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a Game.")); err != nil {
			slog.Error("failed to respond to already playing Game for bot challenge", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create bot Game with level=%d, player=%v cmd: %w", level, player, err)
	}

	embed := createGameStartEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

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
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	channelID := i.ChannelID
	handleExpire := func() {
		if _, err = dg.ChannelMessageSend(channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID)); err != nil {
			slog.Error("failed to send user challenge expire", "err", err)
		}
	}
	CreateChallenge(ctx, h.Cc, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a Game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.ID, player.Name, player.ID)

	if err := dg.InteractionRespond(i.Interaction, createStringResponse(msg)); err != nil {
		slog.Error("failed to respond to user challenge", "err", err)
	}
	return nil
}

func (h *Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := PlayerFromUser(i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %w", err)
	}

	didAccept := AcceptChallenge(ctx, h.Cc, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("Cannot accept a challenge that does not exist.")); err != nil {
			slog.Error("failed to respond to denied accept", "err", err)
		}
		return nil
	}
	game, err := CreateGame(ctx, h.Gc, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create Game with opponent=%v cmd: %w", opponent, err)
	}

	embed := createGameStartEmbed(game)
	img := othello.DrawBoard(h.Rc, game.Board)

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

	game, err := GetGame(ctx, h.Gc, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're not playing a Game.")); err != nil {
			slog.Error("failed to respond to not found view", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %w", user.ID, err)
	}

	embed := createGameEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

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

	game, err := GetGame(ctx, h.Gc, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a Game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %w", user.ID, err)
	}
	DeleteGame(h.Gc, game)
	gr := game.CreateForfeitResult(user.ID)
	sr, err := UpdateStats(ctx, h.Db, gr)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %w", user.ID, err)
	}

	embed := createForfeitEmbed(gr, sr)
	img := othello.DrawBoard(h.Rc, game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to forfeit", "err", err)
	}
	return nil
}

func (h *Handler) HandleMoveAutocomplete(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) {
	var moves []othello.Tile
	if i.Interaction.Member != nil {
		game, err := GetGame(ctx, h.Gc, i.Interaction.Member.User.ID)
		if err == nil {
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
	var gr GameResult
	var sr StatsResult
	var err error

	gr = game.CreateResult()
	if sr, err = UpdateStats(ctx, h.Db, gr); err != nil {
		return nil, nil, fmt.Errorf("failed to update stats for result=%v: %w", gr, err)
	}

	embed := createGameOverEmbed(game, gr, sr, move)
	img := othello.DrawBoard(h.Rc, game.Board)
	return embed, img, err
}

func (h *Handler) handleBotMove(ctx context.Context, dg *discordgo.Session, channelID string, game Game) {
	request := WorkerRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(GetBotLevel(game.CurrentPlayer().ID)),
		T:        GetMoveRequest,
		RespChan: make(chan []othello.RankTile, 1),
	}
	h.Wq <- request

	moves := <-request.RespChan
	if len(moves) != 1 {
		panic("expected exactly engine to respond with one move") // assert that the bot can make a move
	}
	move := moves[0].Tile

	game = MakeMoveUnchecked(h.Gc, game.OtherPlayer().ID, move) // Game will be stored at the ID of the player that is NOT the bot

	var embed *discordgo.MessageEmbed
	var img image.Image
	var err error

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			slog.Error("failed to send game over bot move", "err", err)
		}
	} else {
		embed = createGameMoveEmbed(game, move)
		img = othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())
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
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := MakeMoveValidated(h.Gc, player.ID, move)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently playing a Game."))
		return nil
	} else if errors.Is(err, ErrInvalidMove) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse(fmt.Sprintf("Can't make a Move to %s.", moveStr)))
		return nil
	} else if errors.Is(err, ErrTurn) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("It isn't your turn."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to make Move=%v for player=%s: %w", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			return err
		}
	} else {
		isBot := game.CurrentPlayer().isBot()
		if isBot {
			embed = createGameEmbed(game)
			go h.handleBotMove(ctx, dg, i.ChannelID, game)
		} else {
			embed = createGameMoveEmbed(game, move)
		}
		img = othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to Move", "err", err)
	}
	return nil
}

func (h *Handler) HandleAnalyze(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	trace := ctx.Value("trace")

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

	game, err := GetGame(ctx, h.Gc, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently in a Game."))
		return nil
	}

	request := WorkerRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(level),
		T:        GetMovesRequest,
		RespChan: make(chan []othello.RankTile, 1),
	}
	h.Wq <- request

	if err := dg.InteractionRespond(i.Interaction, createStringResponse("Analyzing... Wait a second...")); err != nil {
		slog.Error("failed to respond to analyze", "err", err)
	}

	select {
	case resp := <-request.RespChan:
		embed := createAnalysisEmbed(game, level)
		img := othello.DrawBoardAnalysis(h.Rc, game.Board, resp)

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

func (h *Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Hour*1)) // a simulation can stay paused for up to an hour
	defer cancel()

	trace := ctx.Value("trace")
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
	img := othello.DrawBoard(h.Rc, initialGame.Board)

	simulationID := uuid.New().String()

	components := createSimulationActionRow(simulationID, SimPlaying)
	if err := dg.InteractionRespond(i.Interaction, createComponentResponse(embed, img, components)); err != nil {
		slog.Error("failed to respond to simulate", "err", err)
		return nil
	}

	state := &SimState{StopChan: make(chan struct{})}
	h.Ss.Set(simulationID, state, SimulationTtl)

	// background task to calculate the simulation messages to send
	simChan := make(chan SimMsg, SimCount)
	go Simulation(ctx, h.Wq, initialGame, simChan)

	slog.Info("simulation receiver started", "delay", delay, "trace", trace)

	// receive simulation events and respond to the client accordingly
	simCtx := SimContext{
		Ctx:      ctx,
		State:    state,
		Cancel:   cancel,
		RecvChan: simChan,
	}
	send := func(embed *discordgo.MessageEmbed, img image.Image) {
		if _, err := dg.InteractionResponseEdit(i.Interaction, createEmbedEdit(embed, img)); err != nil {
			slog.Error("failed to edit message simulate", "err", err)
		}
	}
	ReceiveSimulate(simCtx, h.Rc, send, delay)
	return nil
}

func (h *Handler) HandleStats(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	var err error

	userOpt := i.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		if user, err = h.Uc.GetUser(ctx, userOpt.Value.(string)); err != nil {
			return err
		}
	} else if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	}
	if user == nil {
		return ErrUserNotProvided
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.Uc, user.ID); err != nil {
		return err
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

	if stats, err = ReadTopStats(ctx, h.Db, h.Uc, LeaderboardSize); err != nil {
		return err
	}

	embed := createLeaderboardEmbed(stats)
	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond to leaderboard", "err", err)
	}
	return nil
}

func (h *Handler) HandlePauseComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.Ss.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	simulationID = item.Key()
	state := item.Value()

	isPaused := state.IsPaused.Toggle()
	isPaused = !isPaused // isPaused will return the previous value, so we need to reapply the toggle on the local copy we retrieved

	if err := dg.InteractionRespond(i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}); err != nil {
		slog.Error("failed to follow up with pause message", "err", err)
	}

	simCond := SimPaused
	if !isPaused {
		simCond = SimPlaying
	}
	components := createSimulationActionRow(simulationID, simCond)
	if _, err := dg.InteractionResponseEdit(i.Interaction, &discordgo.WebhookEdit{Components: &components}); err != nil {
		slog.Error("failed to edit pause component", "err", err)
	}
	return nil
}

func (h *Handler) HandleStopComponent(dg *discordgo.Session, i *discordgo.InteractionCreate, simulationID string) error {
	item := h.Ss.Get(simulationID)
	if item == nil {
		return fmt.Errorf("simulation expired: %v", simulationID)
	}
	state := item.Value()
	state.StopChan <- struct{}{}

	if err := dg.InteractionRespond(i.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}); err != nil {
		slog.Error("failed to follow up with stop message", "err", err)
	}

	components := createSimulationActionRow(simulationID, SimStopped)
	if _, err := dg.InteractionResponseEdit(i.Interaction, &discordgo.WebhookEdit{Components: &components}); err != nil {
		slog.Error("failed to edit stop component", "err", err)
	}
	return nil
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, err error) {
	trace := ctx.Value("trace")
	slog.Error("error when handling command", "trace", trace, "err", err)

	content := "an unexpected error occurred"

	switch err.(type) {
	case *SubCmdError:
		content = err.Error()
	case *OptionError:
		content = err.Error()
	}
	content = formatError(content)

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
