package app

import (
	"context"
	"errors"
	"fmt"
	"image"
	"log/slog"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
)

type Handler struct {
	discord        DiscordAPI
	shell          NTestShellAPI
	renderer       *Renderer
	userCache      *UserCache
	statsService   *StatsService
	gameService    *GameService
	challengeCache ChallengeCache
	simCache       SimCache

	defaultDelay time.Duration
}

func MakeHandler(db *sqlx.DB, dg *discordgo.Session, sh *NTestShellPool) Handler {
	userCache := MakeUserCache(dg)
	return Handler{
		discord:        dg,
		shell:          sh,
		userCache:      userCache,
		statsService:   MakeStatsService(db, userCache),
		gameService:    MakeGameService(db),
		renderer:       MakeRenderCache(),
		challengeCache: MakeChallengeCache(),
		simCache:       MakeSimCache(),

		defaultDelay: DefaultDelay,
	}
}

var ErrUserNotProvided = errors.New("user not provided")

func (h *Handler) HandleInteractionCreate(ctx context.Context, ic *discordgo.InteractionCreate) {
	ctx = context.WithValue(ctx, TraceKey, uuid.NewString())
	resp := h.handeInteraction(ctx, ic)
	handleResponseSend(ctx, h.discord, ic, resp)
}

func (h *Handler) handeInteraction(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	slog.InfoContext(ctx, "handling interaction", "type", ic.Type)

	switch ic.Type {
	case discordgo.InteractionApplicationCommand, discordgo.InteractionApplicationCommandAutocomplete:
		cmd := ic.ApplicationCommandData()
		slog.InfoContext(ctx, "received a command", "name", cmd.Name, "options", formatOptions(cmd.Options))

		switch cmd.Name {
		case "challenge":
			return h.HandleChallenge(ctx, ic)
		case "accept":
			return h.HandleAccept(ctx, ic)
		case "forfeit":
			return h.HandleForfeit(ctx, ic)
		case "move":
			if ic.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				return h.HandleMoveAutocomplete(ctx, ic)
			} else {
				return h.HandleMove(ctx, ic)
			}
		case "view":
			return h.HandleView(ctx, ic)
		case "analyze":
			return h.HandleAnalyze(ctx, ic)
		case "simulate":
			return h.HandleSimulate(ctx, ic)
		case "stats":
			return h.HandleStats(ctx, ic)
		case "leaderboard":
			return h.HandleLeaderboard(ctx, ic)
		case "moves":
			return h.HandleMoves(ctx, ic)
		}
	case discordgo.InteractionMessageComponent:
		msg := ic.MessageComponentData()
		slog.InfoContext(ctx, "received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			return h.HandlePauseComponent(ctx, ic, key)
		case SimStopKey:
			return h.HandleStopComponent(key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
	}

	return nil
}

var ChallengeSubCmds = []string{"bot", "user"}

func (h *Handler) HandleChallenge(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	subCmd, options := getSubcommand(ic)
	switch subCmd {
	case "bot":
		return h.HandleBotChallengeCommand(ctx, ic, options)
	case "user":
		return h.HandleUserChallengeCommand(ctx, ic, options)
	default:
		return InteractionError{Err: SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}}
	}
}

func (h *Handler) HandleBotChallengeCommand(
	ctx context.Context,
	ic *discordgo.InteractionCreate,
	options []*discordgo.ApplicationCommandInteractionDataOption,
) HandlerResponse {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		return InteractionError{Err: err}
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return InteractionError{Err: ErrUserNotProvided}
	}

	game, err := h.gameService.CreateBotGame(ctx, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		return InteractionResponse{Response: makeStringResponse("You're already in a game.")}
	}
	if err != nil {
		return InteractionError{Err: fmt.Errorf("make game with level = %d, player = %v: %w", level, player, err)}
	}

	embed := makeGameStartEmbed(game)
	img := h.renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func (h *Handler) HandleUserChallengeCommand(
	ctx context.Context,
	ic *discordgo.InteractionCreate,
	options []*discordgo.ApplicationCommandInteractionDataOption,
) HandlerResponse {
	opponent, err := getPlayerOpt(ctx, h.userCache, options, "opponent")
	if err != nil {
		return InteractionError{Err: err}
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return InteractionError{Err: ErrUserNotProvided}
	}

	channelID := ic.ChannelID
	handleExpire := func() {
		channelMessageSend(ctx, h.discord, channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID))
	}
	h.challengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline.", opponent.ID, player.Name, player.ID)
	return InteractionResponse{Response: makeStringResponse(msg)}
}

func (h *Handler) HandleAccept(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	cmd := ic.ApplicationCommandData()
	player := MakeHumanPlayer(ic.Interaction.Member.User)

	opponent, err := getPlayerOpt(ctx, h.userCache, cmd.Options, "challenger")
	if err != nil {
		return InteractionError{Err: err}
	}

	didAccept := h.challengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		return InteractionResponse{Response: makeStringResponse("Cannot accept a challenge that does not exist.")}
	}
	game, err := h.gameService.CreateGame(ctx, opponent, player)
	if errors.Is(err, ErrAlreadyPlaying) {
		return InteractionResponse{Response: makeStringResponse("One or more participants is already in a game.")}
	}
	if err != nil {
		return InteractionError{Err: fmt.Errorf("make game with opponent = %v cmd: %w", opponent, err)}
	}

	embed := makeGameStartEmbed(game)
	img := h.renderer.DrawBoard(game.Board)

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func (h *Handler) handleGetGame(ctx context.Context, ic *discordgo.InteractionCreate) (OthelloGame, *discordgo.User, HandlerResponse) {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return OthelloGame{}, nil, InteractionError{Err: ErrUserNotProvided}
	}

	game, err := h.gameService.GetGame(ctx, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		return OthelloGame{}, nil, InteractionResponse{Response: makeStringResponse("You're not playing a game.")}
	} else if err != nil {
		return OthelloGame{}, nil, InteractionError{Err: fmt.Errorf("get game for player = %s: %w", user.ID, err)}
	}

	return game, user, nil
}

func (h *Handler) HandleView(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	game, _, resp := h.handleGetGame(ctx, ic)
	if resp != nil {
		return resp
	}

	embed := makeGameEmbed(game)
	img := h.renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func (h *Handler) HandleForfeit(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	game, user, resp := h.handleGetGame(ctx, ic)
	if resp != nil {
		return resp
	}

	gameResult := game.CreateForfeitResult(user.ID)
	statsResult, err := h.gameService.UpdateGameOver(ctx, game, gameResult)
	if err != nil {
		return InteractionError{Err: fmt.Errorf("delete game in forfeit: %w", err)}
	}

	embed := makeForfeitEmbed(gameResult, statsResult)
	img := h.renderer.DrawBoard(game.Board)

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func (h *Handler) HandleMoveAutocomplete(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	var moves []Tile
	if ic.Interaction.Member != nil {
		if game, err := h.gameService.GetGame(ctx, ic.Interaction.Member.User.ID); err == nil {
			moves = game.Board.FindCurrentMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	return InteractionResponse{Response: makeAutocompleteResponse(choices)}
}

func (h *Handler) handleMoveAgainstBot(ctx context.Context, ic *discordgo.InteractionCreate, game OthelloGame, move Tile) HandlerResponse {
	embed := makeGameEmbed(game)
	img := h.renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
	interactionRespond(ctx, h.discord, ic.Interaction, makeEmbedResponse(embed, img))

	botPlayer := game.CurrentPlayer()
	botLevel := botPlayer.LevelToSearchDepth()
	targetPlayer := game.OtherPlayer()

	for game.HasMoves() {
		resp, err := h.shell.FindBestMove(ctx, game, botLevel)
		if err != nil {
			return InteractionError{Err: fmt.Errorf("retrieve analyis data from engine: %w", ctx.Err())}
		}

		move := resp.Move.Tile
		moveKind := game.MakeMove(move)

		embed := makeGameMoveEmbed(game, move, botPlayer)
		img := h.renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		channelMessageSendComplex(ctx, h.discord, ic.ChannelID, makeEmbedSend(embed, img, targetPlayer))

		slog.InfoContext(ctx, "computed bot move in temporary game state", "game", game, "move", move, "moveKind", moveKind)

		if moveKind != Pass {
			break
		}
	}

	slog.InfoContext(ctx, "updating game state after bot moves", "game", game)

	statsResult, err := h.gameService.UpdateGame(ctx, game)
	if err != nil {
		return InteractionError{Err: fmt.Errorf("update game: %w", err)}
	}

	if game.IsOver() {
		embed := makeGameOverEmbed(game, game.MakeResult(), statsResult, move)
		img := h.renderer.DrawBoard(game.Board)
		return ChannelMessageSendComplexResponse{ChannelID: ic.ChannelID, Data: makeEmbedSend(embed, img, targetPlayer)}
	}

	return nil
}

func (h *Handler) HandleMove(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	move, moveStr, err := getTileOpt(ic.ApplicationCommandData().Options, "move")
	if err != nil {
		return InteractionError{Err: err}
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		return InteractionError{Err: ErrUserNotProvided}
	}

	slog.InfoContext(ctx, "handling make move command", "move", move, "player", player.ID)

	game, statsResult, err := h.gameService.MakeMoveAgainstHuman(ctx, MoveAgainstHuman{player.ID, move})
	switch {
	case errors.Is(err, ErrIsAgainstBot):
		return h.handleMoveAgainstBot(ctx, ic, game, move)
	case errors.Is(err, ErrGameNotFound):
		return InteractionResponse{Response: makeStringResponse("You're not currently playing a game.")}
	case errors.Is(err, ErrInvalidMove):
		return InteractionResponse{Response: makeStringResponse(fmt.Sprintf("Can't make a move to %s.", moveStr))}
	case errors.Is(err, ErrTurn):
		return InteractionResponse{Response: makeStringResponse("It isn't your turn.")}
	case err != nil:
		return InteractionError{Err: fmt.Errorf("make move against human: %w", err)}
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsOver() {
		img = h.renderer.DrawBoard(game.Board)
		embed = makeGameOverEmbed(game, game.MakeResult(), statsResult, move)
	} else {
		img = h.renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		embed = makeGameMoveEmbed(game, move, game.OtherPlayer())
	}

	return MakeManyResponses(
		InteractionResponse{Response: makeEmbedResponse(embed, img)},
		ChannelMessageSendComplexResponse{
			ChannelID: ic.ChannelID,
			Data:      &discordgo.MessageSend{Content: fmt.Sprintf("<@%s>", game.CurrentPlayer().ID)},
		},
	)
}

func (h *Handler) HandleAnalyze(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	ctx, cancel := context.WithTimeout(ctx, time.Minute*2)
	defer cancel()

	level, err := getLevelOpt(ic.ApplicationCommandData().Options, "level")
	if err != nil {
		return InteractionError{Err: err}
	}
	game, _, resp := h.handleGetGame(ctx, ic)
	if resp != nil {
		return resp
	}
	interactionRespond(ctx, h.discord, ic.Interaction, makeStringResponse("Analyzing... Wait a second..."))

	slog.InfoContext(ctx, "starting analysis", "level", level)

	moveResult, err := h.shell.FindRankedMoves(ctx, game, LevelToSearchDepth(level))
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		slog.WarnContext(ctx, "client timed out while waiting for an analysis response", "err", ctx.Err())
		return InteractionResponseEdit{Edit: makeStringEdit("Timed out while waiting for a response.")}
	} else if err != nil {
		return InteractionResponseEdit{Edit: makeEmbedTextEdit("Failed to retrieve analysis data from engine.")}
	}

	embed := makeAnalysisEmbed(game, level)
	img := h.renderer.DrawBoardAnalysis(game.Board, moveResult.Moves)

	return InteractionResponseEdit{Edit: makeEmbedEdit(embed, img)}
}

func (h *Handler) HandleSimulate(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	ctx, cancel := context.WithTimeout(ctx, time.Hour*1) // a simulation can stay paused for up to an hour
	defer cancel()

	cmd := ic.ApplicationCommandData()

	whiteLevel, err := getLevelOpt(cmd.Options, "white-level")
	if err != nil {
		return InteractionError{Err: err}
	}
	blackLevel, err := getLevelOpt(cmd.Options, "black-level")
	if err != nil {
		return InteractionError{Err: err}
	}
	delay, err := getDelayOpt(cmd.Options, "delay", h.defaultDelay)
	if err != nil {
		return InteractionError{Err: err}
	}

	slog.InfoContext(ctx, "starting simulation", "whiteLevel", whiteLevel, "blackLevel", blackLevel, "delay", delay)

	initialGame := OthelloGame{
		WhitePlayer: MakeBotPlayer(whiteLevel),
		BlackPlayer: MakeBotPlayer(blackLevel),
		Board:       MakeInitialBoard(),
	}

	embed := makeSimulationStartEmbed(initialGame)
	img := h.renderer.DrawBoard(initialGame.Board)
	simulationID := uuid.New().String()
	actionRowResp := makeComponentResponse(embed, img, makeSimulationActionRow(simulationID, false))
	interactionRespond(ctx, h.discord, ic.Interaction, actionRowResp)

	// run the simulation against the engine and add it to the cache (so it can be paused/resumed)
	simState := &SimState{Cancel: cancel}
	simChan := make(chan SimStep, MaxSimCount) // give this a size so we don't block on sending

	h.simCache.Set(simulationID, simState, SimulationTtl)

	go generateSimulation(ctx, h.shell, initialGame, simChan)
	finalSimResp := h.recvSimulation(ctx, ic, delay, simState, simChan)

	return finalSimResp
}

func (h *Handler) recvSimulation(
	ctx context.Context,
	ic *discordgo.InteractionCreate,
	delay time.Duration,
	simState *SimState,
	simChan chan SimStep,
) HandlerResponse {
	ticker := time.NewTicker(delay)
	for {
		select {
		case <-ctx.Done():
			slog.InfoContext(ctx, "simulation receiver stopped", "simState", simState)
			return InteractionResponseEdit{Edit: &discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}}}
		case <-ticker.C:
			if simState.IsPaused.Load() { // paused? check again once the ticker executes
				continue
			}
			step, ok := <-simChan
			if !ok {
				slog.InfoContext(ctx, "simulation receiver complete", "simState", simState)
				return nil
			}
			interactionResponseEdit(ctx, h.discord, ic.Interaction, makeStepEdit(h.renderer, step))
		}
	}
}

func (h *Handler) HandleStats(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	user, err := getDefaultPlayer(ctx, h.userCache, ic)
	if err != nil {
		return InteractionError{Err: err}
	}
	if user == nil {
		return InteractionError{Err: ErrUserNotProvided}
	}

	stats, err := h.statsService.ReadStats(ctx, user.ID)
	if err != nil {
		return InteractionError{Err: err}
	}

	embed := makeStatsEmbed(user, stats)

	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

const LeaderboardSize = 50

func (h *Handler) HandleLeaderboard(ctx context.Context, _ *discordgo.InteractionCreate) HandlerResponse {
	stats, err := h.statsService.ReadTopStats(ctx, LeaderboardSize)
	if err != nil {
		return InteractionError{Err: err}
	}
	embed := makeLeaderboardEmbed(stats)
	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

func (h *Handler) HandleMoves(ctx context.Context, ic *discordgo.InteractionCreate) HandlerResponse {
	game, _, resp := h.handleGetGame(ctx, ic)
	if resp != nil {
		return resp
	}
	embed := makeMovesEmbed(game)
	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

func (h *Handler) HandlePauseComponent(ctx context.Context, ic *discordgo.InteractionCreate, simulationID string) HandlerResponse {
	item := h.simCache.Get(simulationID)
	if item == nil {
		return InteractionResponse{Response: &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}}
	}

	simulationID = item.Key()
	simState := item.Value()

	isPaused := !simState.IsPaused.Toggle() // negate this because it returns the old value

	interactionRespond(ctx, h.discord, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})

	components := makeSimulationActionRow(simulationID, isPaused)

	return InteractionResponseEdit{Edit: &discordgo.WebhookEdit{Components: &components}}
}

func (h *Handler) HandleStopComponent(simulationID string) HandlerResponse {
	acknowledge := func() HandlerResponse {
		return InteractionResponse{Response: &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}}
	}

	item := h.simCache.Get(simulationID)
	if item == nil {
		return acknowledge()
	}

	simState := item.Value()
	simState.Cancel()

	return acknowledge()
}
