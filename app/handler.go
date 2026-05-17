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
	Dg             *discordgo.Session
	Db             *sqlx.DB
	Sh             *NTestShellPool
	Renderer       Renderer
	UserCache      UserCache
	ChallengeCache ChallengeCache
	SimCache       SimCache
}

func MakeState(db *sqlx.DB, dg *discordgo.Session, sh *NTestShellPool) State {
	return State{
		Db:             db,
		Dg:             dg,
		Sh:             sh,
		Renderer:       MakeRenderCache(),
		ChallengeCache: MakeChallengeCache(),
		UserCache:      MakeUserCache(dg),
		SimCache:       MakeSimCache(),
	}
}

var ErrUserNotProvided = errors.New("user not provided")

func (state *State) HandeInteractionCreate(_ *discordgo.Session, ic *discordgo.InteractionCreate) {
	trace := uuid.NewString()
	ctx := context.WithValue(context.Background(), TraceKey, trace)

	switch ic.Type {
	case discordgo.InteractionApplicationCommandAutocomplete:
		fallthrough
	case discordgo.InteractionApplicationCommand:
		cmd := ic.ApplicationCommandData()
		slog.Info("received a command", "trace", trace, "name", cmd.Name, "options", formatOptions(cmd.Options))

		switch cmd.Name {
		case "challenge":
			HandleChallenge(ctx, state, ic)
		case "accept":
			HandleAccept(ctx, state, ic)
		case "forfeit":
			HandleForfeit(ctx, state, ic)
		case "move":
			if ic.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				HandleMoveAutocomplete(ctx, state, ic)
			} else {
				HandleMove(ctx, state, ic)
			}
		case "view":
			HandleView(ctx, state, ic)
		case "analyze":
			HandleAnalyze(ctx, state, ic)
		case "simulate":
			HandleSimulate(ctx, state, ic)
		case "stats":
			HandleStats(ctx, state, ic)
		case "leaderboard":
			HandleLeaderboard(ctx, state, ic)
		case "moves":
			HandleMoves(ctx, state, ic)
		}
	case discordgo.InteractionMessageComponent:
		msg := ic.MessageComponentData()
		slog.Info("received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			HandlePauseComponent(state, ic, key)
		case SimStopKey:
			HandleStopComponent(state, ic, key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
	}
}

var ChallengeSubCmds = []string{"bot", "user"}

func HandleChallenge(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	subCmd, options := getSubcommand(ic)
	switch subCmd {
	case "bot":
		HandleBotChallengeCommand(ctx, state, ic, options)
	case "user":
		HandleUserChallengeCommand(ctx, state, ic, options)
	default:
		handleInteractionError(ctx, state.Dg, ic, SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds})
		return
	}
}

func HandleBotChallengeCommand(ctx context.Context, state *State, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		handleInteractionError(ctx, state.Dg, ic, ErrUserNotProvided)
		return
	}

	game, err := CreateBotGameTx(ctx, state.Db, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		interactionRespond(state.Dg, ic.Interaction, makeStringResponse("You're already in a game."))
		return
	}
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, fmt.Errorf("failed to make game with level=%d, player=%v: %w", level, player, err))
		return
	}

	embed := makeGameStartEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))
}

func HandleUserChallengeCommand(ctx context.Context, state *State, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) {
	opponent, err := getPlayerOpt(ctx, &state.UserCache, options, "opponent")
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}

	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		handleInteractionError(ctx, state.Dg, ic, ErrUserNotProvided)
		return
	}

	channelID := ic.ChannelID
	handleExpire := func() {
		channelMessageSend(state.Dg, channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID))
	}
	state.ChallengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline.", opponent.ID, player.Name, player.ID)

	interactionRespond(state.Dg, ic.Interaction, makeStringResponse(msg))
}

func HandleAccept(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	cmd := ic.ApplicationCommandData()
	player := MakeHumanPlayer(ic.Interaction.Member.User)

	opponent, err := getPlayerOpt(ctx, &state.UserCache, cmd.Options, "challenger")
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}

	didAccept := state.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		interactionRespond(state.Dg, ic.Interaction, makeStringResponse("Cannot accept a challenge that does not exist."))
		return
	}
	game, err := CreateGameTx(ctx, state.Db, opponent, player)
	if errors.Is(err, ErrAlreadyPlaying) {
		interactionRespond(state.Dg, ic.Interaction, makeStringResponse("One or more participants is already in a game."))
		return
	}
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, fmt.Errorf("failed to make game with opponent=%v cmd: %w", opponent, err))
		return
	}

	embed := makeGameStartEmbed(game)
	img := state.Renderer.DrawBoard(game.Board)

	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))
}

func handleGetGame(ctx context.Context, state *State, ic *discordgo.InteractionCreate) (OthelloGame, *discordgo.User, func()) {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return OthelloGame{}, nil, func() { handleInteractionError(ctx, state.Dg, ic, ErrUserNotProvided) }
	}

	game, err := GetGame(ctx, state.Db, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		return OthelloGame{}, nil, func() { interactionRespond(state.Dg, ic.Interaction, makeStringResponse("You're not playing a game.")) }
	}
	if err != nil {
		return OthelloGame{}, nil, func() {
			handleInteractionError(ctx, state.Dg, ic, fmt.Errorf("failed to get game for player=%s: %w", user.ID, err))
		}
	}

	return game, user, nil
}

func HandleView(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	game, _, handleError := handleGetGame(ctx, state, ic)
	if handleError != nil {
		handleError()
		return
	}

	embed := makeGameEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))
}

func HandleForfeit(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	game, user, handleError := handleGetGame(ctx, state, ic)
	if handleError != nil {
		handleError()
		return
	}

	gr := game.CreateForfeitResult(user.ID)
	sr, err := GameOverTx(ctx, state.Db, game, gr)
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, fmt.Errorf("failed to delete game in forfeit: %s", err))
		return
	}

	embed := makeForfeitEmbed(gr, sr)
	img := state.Renderer.DrawBoard(game.Board)
	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))
}

func HandleMoveAutocomplete(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	var moves []Tile
	if ic.Interaction.Member != nil {
		if game, err := GetGame(ctx, state.Db, ic.Interaction.Member.User.ID); err == nil {
			moves = game.Board.FindCurrentMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	interactionRespond(state.Dg, ic.Interaction, makeAutocompleteResponse(choices))
}

func respondMoveByHuman(state *State, ic *discordgo.InteractionCreate, game OthelloGame, sr StatsResult, move Tile) {
	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsOver() {
		img = state.Renderer.DrawBoard(game.Board)
		embed = makeGameOverEmbed(game, game.CreateResult(), sr, move)
	} else {
		img = state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		embed = makeGameMoveEmbed(game, move, game.OtherPlayer())
	}

	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))
	channelMessageSendComplex(state.Dg, ic.ChannelID, &discordgo.MessageSend{Content: fmt.Sprintf("<@%s>", game.CurrentPlayer().ID)})
}

func handleMoveAgainstBot(ctx context.Context, state *State, ic *discordgo.InteractionCreate, game OthelloGame, move Tile) {
	trace := ctx.Value(TraceKey)

	handleErr := func(err error) {
		slog.Error("failed to handle bot move", "trace", trace, "err", err)
		channelMessageSendComplex(state.Dg, ic.ChannelID, makeStringSend(InternalServerErrorMsg))
	}

	embed := makeGameEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, img))

	botPlayer := game.CurrentPlayer()
	botLevel := botPlayer.LevelToDepth()
	targetPlayer := game.OtherPlayer()

	for game.HasMoves() {
		resp, err := state.Sh.FindBestMove(ctx, game, botLevel)
		if err != nil {
			handleErr(fmt.Errorf("failed to retrieve analyis data from engine: %w", ctx.Err()))
			return
		}

		move := resp.Move.Tile
		moveKind := game.MakeMove(move)

		embed := makeGameMoveEmbed(game, move, botPlayer)
		img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		channelMessageSendComplex(state.Dg, ic.ChannelID, makeEmbedSend(embed, img, targetPlayer))

		if moveKind != Pass {
			break
		}
	}

	sr, err := UpdateGame(ctx, state.Db, game)
	if err != nil {
		handleErr(fmt.Errorf("failed to update game: %w", err))
		return
	}

	if game.IsOver() {
		embed := makeGameOverEmbed(game, game.CreateResult(), sr, move)
		img := state.Renderer.DrawBoard(game.Board)
		channelMessageSendComplex(state.Dg, ic.ChannelID, makeEmbedSend(embed, img, targetPlayer))
	}
}

func HandleMove(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	move, moveStr, err := getTileOpt(ic.ApplicationCommandData().Options, "move")
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	var player Player
	if ic.Interaction.Member != nil {
		player = MakeHumanPlayer(ic.Interaction.Member.User)
	} else {
		handleInteractionError(ctx, state.Dg, ic, ErrUserNotProvided)
		return
	}

	game, sr, err := MakeMoveAgainstHuman(ctx, state.Db, MoveAgainstHuman{player.ID, move})

	if errors.Is(err, ErrIsAgainstBot) {
		handleMoveAgainstBot(ctx, state, ic, game, move)
		return
	} else {
		if resp := makeMoveErrorResp(err, moveStr); resp != nil {
			interactionRespond(state.Dg, ic.Interaction, resp)
			return
		}
		if err != nil {
			handleInteractionError(ctx, state.Dg, ic, fmt.Errorf("failed to make move against human: %w", err))
			return
		}
	}
	respondMoveByHuman(state, ic, game, sr, move)
}

func HandleAnalyze(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	trace := ctx.Value(TraceKey)

	ctx, cancel := context.WithTimeout(ctx, time.Minute*2)
	defer cancel()

	level, err := getLevelOpt(ic.ApplicationCommandData().Options, "level")
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	game, _, handleError := handleGetGame(ctx, state, ic)
	if handleError != nil {
		handleError()
		return
	}

	interactionRespond(state.Dg, ic.Interaction, makeStringResponse("Analyzing... Wait a second..."))

	resp, err := state.Sh.FindRankedMoves(ctx, game, LevelToDepth(level))
	if errors.Is(err, context.Canceled) {
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		interactionResponseEdit(state.Dg, ic.Interaction, makeStringEdit("Timed out while waiting for a response."))
	} else if err != nil {
		interactionResponseEdit(state.Dg, ic.Interaction, makeEmbedTextEdit("Failed to retrieve analysis data from engine."))
		return
	}

	embed := makeAnalysisEmbed(game, level)
	img := state.Renderer.DrawBoardAnalysis(game.Board, resp.Moves)
	interactionResponseEdit(state.Dg, ic.Interaction, makeEmbedEdit(embed, img))
}

func HandleSimulate(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	ctx, cancel := context.WithTimeout(ctx, time.Hour*1) // a simulation can stay paused for up to an hour
	defer cancel()

	// extract simulation inputs and send the initial simulation response
	cmd := ic.ApplicationCommandData()

	var whiteLevel uint64
	var blackLevel uint64
	var delay time.Duration
	var err error

	if whiteLevel, err = getLevelOpt(cmd.Options, "white-level"); err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	if blackLevel, err = getLevelOpt(cmd.Options, "black-level"); err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	if delay, err = getDelayOpt(cmd.Options, "delay"); err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}

	initialGame := OthelloGame{
		WhitePlayer: MakeBotPlayer(whiteLevel),
		BlackPlayer: MakeBotPlayer(blackLevel),
		Board:       MakeInitialBoard(),
	}
	embed := makeSimulationStartEmbed(initialGame)
	img := state.Renderer.DrawBoard(initialGame.Board)

	simulationID := uuid.New().String()

	response := makeComponentResponse(embed, img, makeSimulationActionRow(simulationID, false))
	interactionRespond(state.Dg, ic.Interaction, response)

	// run the simulation against the engine and add it to the cache (so it can be paused/resumed)
	simState := &SimState{Cancel: cancel}
	simChan := make(chan SimStep, MaxSimCount) // give this a size so we don't block on send

	state.SimCache.Set(simulationID, simState, SimulationTtl)

	go GenerateSimulation(ctx, state.Sh, initialGame, simChan)
	RecvSimulation(ctx, state, ic, delay, simState, simChan)
}

func RecvSimulation(ctx context.Context, state *State, ic *discordgo.InteractionCreate, delay time.Duration, simState *SimState, simChan chan SimStep) {
	trace := ctx.Value(TraceKey)

	ticker := time.NewTicker(delay)
	for {
		select {
		case <-ctx.Done():
			slog.Info("simulation receiver stopped", "trace", trace)
			interactionResponseEdit(state.Dg, ic.Interaction, &discordgo.WebhookEdit{Components: &[]discordgo.MessageComponent{}})
			return
		case <-ticker.C:
			if simState.IsPaused.Load() { // paused? check again once the ticker executes
				continue
			}
			step, ok := <-simChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return
			}
			interactionResponseEdit(state.Dg, ic.Interaction, makeStepEdit(state.Renderer, step))
		}
	}
}

func HandleStats(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	user, err := getDefaultPlayer(ctx, &state.UserCache, ic)
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	if user == nil {
		handleInteractionError(ctx, state.Dg, ic, ErrUserNotProvided)
		return
	}

	var stats Stats
	if stats, err = ReadStats(ctx, state.Db, state.UserCache, user.ID); err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}

	embed := makeStatsEmbed(user, stats)
	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, nil))
}

const LeaderboardSize = 50

func HandleLeaderboard(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	stats, err := ReadTopStats(ctx, state.Db, state.UserCache, LeaderboardSize)
	if err != nil {
		handleInteractionError(ctx, state.Dg, ic, err)
		return
	}
	embed := makeLeaderboardEmbed(stats)
	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, nil))
}

func HandleMoves(ctx context.Context, state *State, ic *discordgo.InteractionCreate) {
	game, _, handleError := handleGetGame(ctx, state, ic)
	if handleError != nil {
		handleError()
		return
	}
	embed := makeMovesEmbed(game)
	interactionRespond(state.Dg, ic.Interaction, makeEmbedResponse(embed, nil))
}

func HandlePauseComponent(state *State, ic *discordgo.InteractionCreate, simulationID string) {
	acknowledge := func() {
		interactionRespond(state.Dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := state.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return
	}

	simulationID = item.Key()
	simState := item.Value()

	isPaused := !simState.IsPaused.Toggle() // negate this because it returns the old value

	acknowledge()

	components := makeSimulationActionRow(simulationID, isPaused)
	interactionResponseEdit(state.Dg, ic.Interaction, &discordgo.WebhookEdit{Components: &components})
}

func HandleStopComponent(state *State, ic *discordgo.InteractionCreate, simulationID string) {
	acknowledge := func() {
		interactionRespond(state.Dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := state.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return
	}

	simState := item.Value()
	simState.Cancel()

	acknowledge()
}

func channelMessageSend(dg *discordgo.Session, channelID string, str string) {
	if _, err := dg.ChannelMessageSend(channelID, str); err != nil {
		slog.Error("failed to send message", "err", err)
	}
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

const InternalServerErrorMsg = "An unexpected error occurred."

func handleInteractionError(ctx context.Context, dg *discordgo.Session, ic *discordgo.InteractionCreate, err error) {
	trace := ctx.Value(TraceKey)
	slog.Error("error when handling command", "trace", trace, "err", err)

	content := InternalServerErrorMsg

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
