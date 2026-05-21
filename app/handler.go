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

type State struct {
	Discord        *discordgo.Session
	Database       *sqlx.DB
	Shell          *NTestShellPool
	Renderer       Renderer
	UserCache      UserCache
	ChallengeCache ChallengeCache
	SimCache       SimCache
}

func MakeState(db *sqlx.DB, dg *discordgo.Session, sh *NTestShellPool) State {
	return State{
		Database:       db,
		Discord:        dg,
		Shell:          sh,
		Renderer:       MakeRenderCache(),
		ChallengeCache: MakeChallengeCache(),
		UserCache:      MakeUserCache(dg),
		SimCache:       MakeSimCache(),
	}
}

var ErrUserNotProvided = errors.New("user not provided")

func MakeHandleInteractionCreate(state *State) func(_ *discordgo.Session, ic *discordgo.InteractionCreate) {
	return func(_ *discordgo.Session, ic *discordgo.InteractionCreate) {
		trace := uuid.NewString()
		ctx := context.WithValue(context.Background(), TraceKey, trace)

		resp := handeInteractionCreate(ctx, state, ic)

		handleResponseSend(ctx, state.Discord, ic, resp)
	}
}

func handeInteractionCreate(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	trace := ctx.Value(TraceKey)

	switch ic.Type {
	case discordgo.InteractionApplicationCommandAutocomplete:
		fallthrough
	case discordgo.InteractionApplicationCommand:
		cmd := ic.ApplicationCommandData()
		slog.Info("received a command", "trace", trace, "name", cmd.Name, "options", formatOptions(cmd.Options))

		switch cmd.Name {
		case "challenge":
			return HandleChallenge(ctx, state, ic)
		case "accept":
			return HandleAccept(ctx, state, ic)
		case "forfeit":
			return HandleForfeit(ctx, state, ic)
		case "move":
			if ic.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				return HandleMoveAutocomplete(ctx, state, ic)
			} else {
				return HandleMove(ctx, state, ic)
			}
		case "view":
			return HandleView(ctx, state, ic)
		case "analyze":
			return HandleAnalyze(ctx, state, ic)
		case "simulate":
			return HandleSimulate(ctx, state, ic)
		case "stats":
			return HandleStats(ctx, state, ic)
		case "leaderboard":
			return HandleLeaderboard(ctx, state, ic)
		case "moves":
			return HandleMoves(ctx, state, ic)
		}
	case discordgo.InteractionMessageComponent:
		msg := ic.MessageComponentData()
		slog.Info("received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			return HandlePauseComponent(ctx, state, ic, key)
		case SimStopKey:
			return HandleStopComponent(state, key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
	}

	return nil
}

var ChallengeSubCmds = []string{"bot", "user"}

func HandleChallenge(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	subCmd, options := getSubcommand(ic)
	switch subCmd {
	case "bot":
		return HandleBotChallengeCommand(ctx, state, ic, options)
	case "user":
		return HandleUserChallengeCommand(ctx, state, ic, options)
	default:
		return InteractionError{Err: SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}}
	}
}

func HandleBotChallengeCommand(
	ctx context.Context,
	state *State,
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

	game, err := CreateBotGameTx(ctx, state.Database, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		return InteractionResponse{Response: makeStringResponse("You're already in a game.")}
	}
	if err != nil {
		return InteractionError{Err: fmt.Errorf("failed to make game with level=%d, player=%v: %w", level, player, err)}
	}

	embed := makeGameStartEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func HandleUserChallengeCommand(
	ctx context.Context,
	state *State,
	ic *discordgo.InteractionCreate,
	options []*discordgo.ApplicationCommandInteractionDataOption,
) HandlerResponse {
	opponent, err := getPlayerOpt(ctx, &state.UserCache, options, "opponent")
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
		channelMessageSend(ctx, state.Discord, channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID))
	}
	state.ChallengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline.", opponent.ID, player.Name, player.ID)
	return InteractionResponse{Response: makeStringResponse(msg)}
}

func HandleAccept(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	cmd := ic.ApplicationCommandData()
	player := MakeHumanPlayer(ic.Interaction.Member.User)

	opponent, err := getPlayerOpt(ctx, &state.UserCache, cmd.Options, "challenger")
	if err != nil {
		return InteractionError{Err: err}
	}

	didAccept := state.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		return InteractionResponse{Response: makeStringResponse("Cannot accept a challenge that does not exist.")}
	}
	game, err := CreateGameTx(ctx, state.Database, opponent, player)
	if errors.Is(err, ErrAlreadyPlaying) {
		return InteractionResponse{Response: makeStringResponse("One or more participants is already in a game.")}
	}
	if err != nil {
		return InteractionError{Err: fmt.Errorf("failed to make game with opponent=%v cmd: %w", opponent, err)}
	}

	embed := makeGameStartEmbed(game)
	img := state.Renderer.DrawBoard(game.Board)

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func handleGetGame(ctx context.Context, state *State, ic *discordgo.InteractionCreate) (OthelloGame, *discordgo.User, HandlerResponse) {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return OthelloGame{}, nil, InteractionError{Err: ErrUserNotProvided}
	}

	game, err := GetGame(ctx, state.Database, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		return OthelloGame{}, nil, InteractionResponse{Response: makeStringResponse("You're not playing a game.")}
	} else if err != nil {
		return OthelloGame{}, nil, InteractionError{Err: fmt.Errorf("failed to get game for player=%s: %w", user.ID, err)}
	}

	return game, user, nil
}

func HandleView(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	game, _, resp := handleGetGame(ctx, state, ic)
	if resp != nil {
		return resp
	}

	embed := makeGameEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func HandleForfeit(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	game, user, resp := handleGetGame(ctx, state, ic)
	if resp != nil {
		return resp
	}

	gr := game.CreateForfeitResult(user.ID)
	sr, err := GameOverTx(ctx, state.Database, game, gr)
	if err != nil {
		return InteractionError{Err: fmt.Errorf("failed to delete game in forfeit: %w", err)}
	}

	embed := makeForfeitEmbed(gr, sr)
	img := state.Renderer.DrawBoard(game.Board)

	return InteractionResponse{Response: makeEmbedResponse(embed, img)}
}

func HandleMoveAutocomplete(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	var moves []Tile
	if ic.Interaction.Member != nil {
		if game, err := GetGame(ctx, state.Database, ic.Interaction.Member.User.ID); err == nil {
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

func respondMoveByHuman(ctx context.Context, state *State, ic *discordgo.InteractionCreate, game OthelloGame, sr StatsResult, move Tile) {
	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsOver() {
		img = state.Renderer.DrawBoard(game.Board)
		embed = makeGameOverEmbed(game, game.CreateResult(), sr, move)
	} else {
		img = state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		embed = makeGameMoveEmbed(game, move, game.OtherPlayer())
	}

	interactionRespond(ctx, state.Discord, ic.Interaction, makeEmbedResponse(embed, img))
	channelMessageSendComplex(ctx, state.Discord, ic.ChannelID, &discordgo.MessageSend{Content: fmt.Sprintf("<@%s>", game.CurrentPlayer().ID)})
}

func handleMoveAgainstBot(ctx context.Context, state *State, ic *discordgo.InteractionCreate, game OthelloGame, move Tile) HandlerResponse {
	embed := makeGameEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
	interactionRespond(ctx, state.Discord, ic.Interaction, makeEmbedResponse(embed, img))

	botPlayer := game.CurrentPlayer()
	botLevel := botPlayer.LevelToSearchDepth()
	targetPlayer := game.OtherPlayer()

	for game.HasMoves() {
		resp, err := state.Shell.FindBestMove(ctx, game, botLevel)
		if err != nil {
			return InteractionError{Err: fmt.Errorf("failed to retrieve analyis data from engine: %w", ctx.Err())}
		}

		move := resp.Move.Tile
		moveKind := game.MakeMove(move)

		embed := makeGameMoveEmbed(game, move, botPlayer)
		img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		channelMessageSendComplex(ctx, state.Discord, ic.ChannelID, makeEmbedSend(embed, img, targetPlayer))

		if moveKind != Pass {
			break
		}
	}

	statsResult, err := UpdateGame(ctx, state.Database, game)
	if err != nil {
		return InteractionError{Err: fmt.Errorf("failed to update game: %w", err)}
	}

	if game.IsOver() {
		embed := makeGameOverEmbed(game, game.CreateResult(), statsResult, move)
		img := state.Renderer.DrawBoard(game.Board)
		channelMessageSendComplex(ctx, state.Discord, ic.ChannelID, makeEmbedSend(embed, img, targetPlayer))
	}

	return nil
}

func HandleMove(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
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

	game, sr, err := MakeMoveAgainstHuman(ctx, state.Database, MoveAgainstHuman{player.ID, move})

	if errors.Is(err, ErrIsAgainstBot) {
		return handleMoveAgainstBot(ctx, state, ic, game, move)
	} else {
		switch {
		case errors.Is(err, ErrGameNotFound):
			return InteractionResponse{Response: makeStringResponse("You're not currently playing a game.")}
		case errors.Is(err, ErrInvalidMove):
			return InteractionResponse{Response: makeStringResponse(fmt.Sprintf("Can't make a ColorMove to %s.", moveStr))}
		case errors.Is(err, ErrTurn):
			return InteractionResponse{Response: makeStringResponse("It isn't your turn.")}
		case err != nil:
			return InteractionError{Err: fmt.Errorf("failed to make move against human: %w", err)}
		}
	}

	respondMoveByHuman(ctx, state, ic, game, sr, move)
	return nil
}

func HandleAnalyze(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	trace := ctx.Value(TraceKey)

	ctx, cancel := context.WithTimeout(ctx, time.Minute*2)
	defer cancel()

	level, err := getLevelOpt(ic.ApplicationCommandData().Options, "level")
	if err != nil {
		return InteractionError{Err: err}
	}
	game, _, resp := handleGetGame(ctx, state, ic)
	if resp != nil {
		return resp
	}
	interactionRespond(ctx, state.Discord, ic.Interaction, makeStringResponse("Analyzing... Wait a second..."))

	moveResult, err := state.Shell.FindRankedMoves(ctx, game, LevelToSearchDepth(level))
	if errors.Is(err, context.Canceled) {
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		return InteractionResponseEdit{Edit: makeStringEdit("Timed out while waiting for a response.")}
	} else if err != nil {
		return InteractionResponseEdit{Edit: makeEmbedTextEdit("Failed to retrieve analysis data from engine.")}
	}

	embed := makeAnalysisEmbed(game, level)
	img := state.Renderer.DrawBoardAnalysis(game.Board, moveResult.Moves)

	return InteractionResponseEdit{Edit: makeEmbedEdit(embed, img)}
}

func HandleSimulate(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
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
	delay, err := getDelayOpt(cmd.Options, "delay")
	if err != nil {
		return InteractionError{Err: err}
	}

	initialGame := OthelloGame{
		WhitePlayer: MakeBotPlayer(whiteLevel),
		BlackPlayer: MakeBotPlayer(blackLevel),
		Board:       MakeInitialBoard(),
	}
	embed := makeSimulationStartEmbed(initialGame)
	img := state.Renderer.DrawBoard(initialGame.Board)

	simulationID := uuid.New().String()

	actionRowResp := makeComponentResponse(embed, img, makeSimulationActionRow(simulationID, false))
	interactionRespond(ctx, state.Discord, ic.Interaction, actionRowResp)

	// run the simulation against the engine and add it to the cache (so it can be paused/resumed)
	simState := &SimState{Cancel: cancel}
	simChan := make(chan SimStep, MaxSimCount) // give this a size so we don't block on send

	state.SimCache.Set(simulationID, simState, SimulationTtl)

	go GenerateSimulation(ctx, state.Shell, initialGame, simChan)
	finalSimResp := RecvSimulation(ctx, state, ic, delay, simState, simChan)

	return finalSimResp
}

func RecvSimulation(ctx context.Context, state *State, ic *discordgo.InteractionCreate, delay time.Duration, simState *SimState, simChan chan SimStep) HandlerResponse {
	trace := ctx.Value(TraceKey)

	ticker := time.NewTicker(delay)
	for {
		select {
		case <-ctx.Done():
			slog.Info("simulation receiver stopped", "trace", trace)
			return InteractionResponseEdit{Edit: &discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}}}
		case <-ticker.C:
			if simState.IsPaused.Load() { // paused? check again once the ticker executes
				continue
			}
			step, ok := <-simChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return nil
			}
			interactionResponseEdit(ctx, state.Discord, ic.Interaction, makeStepEdit(state.Renderer, step))
		}
	}
}

func HandleStats(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	user, err := getDefaultPlayer(ctx, &state.UserCache, ic)
	if err != nil {
		return InteractionError{Err: err}
	}
	if user == nil {
		return InteractionError{Err: ErrUserNotProvided}
	}

	stats, err := ReadStats(ctx, state.Database, state.UserCache, user.ID)
	if err != nil {
		return InteractionError{Err: err}
	}

	embed := makeStatsEmbed(user, stats)

	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

const LeaderboardSize = 50

func HandleLeaderboard(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	stats, err := ReadTopStats(ctx, state.Database, state.UserCache, LeaderboardSize)
	if err != nil {
		return InteractionError{Err: err}
	}
	embed := makeLeaderboardEmbed(stats)
	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

func HandleMoves(ctx context.Context, state *State, ic *discordgo.InteractionCreate) HandlerResponse {
	game, _, resp := handleGetGame(ctx, state, ic)
	if resp != nil {
		return resp
	}
	embed := makeMovesEmbed(game)
	return InteractionResponse{Response: makeEmbedResponse(embed, nil)}
}

func HandlePauseComponent(ctx context.Context, state *State, ic *discordgo.InteractionCreate, simulationID string) HandlerResponse {
	item := state.SimCache.Get(simulationID)
	if item == nil {
		return InteractionResponse{Response: &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}}
	}

	simulationID = item.Key()
	simState := item.Value()

	isPaused := !simState.IsPaused.Toggle() // negate this because it returns the old value

	interactionRespond(ctx, state.Discord, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})

	components := makeSimulationActionRow(simulationID, isPaused)

	return InteractionResponseEdit{Edit: &discordgo.WebhookEdit{Components: &components}}
}

func HandleStopComponent(state *State, simulationID string) HandlerResponse {
	acknowledge := func() HandlerResponse {
		return InteractionResponse{Response: &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage}}
	}

	item := state.SimCache.Get(simulationID)
	if item == nil {
		return acknowledge()
	}

	simState := item.Value()
	simState.Cancel()

	return acknowledge()
}
