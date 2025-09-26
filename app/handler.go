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

type State struct {
	Dg             *discordgo.Session
	Db             *sql.DB
	Sh             *NTestShell
	TaskCh         chan BotTask
	Renderer       Renderer
	UserCache      UserCache
	ChallengeCache ChallengeCache
	SimCache       SimCache
}

var ErrUserNotProvided = errors.New("user not provided")

func (state *State) HandeInteractionCreate(_ *discordgo.Session, ic *discordgo.InteractionCreate) {
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
			err = HandleChallenge(ctx, state, ic)
		case "accept":
			err = HandleAccept(ctx, state, ic)
		case "forfeit":
			err = HandleForfeit(ctx, state, ic)
		case "move":
			if ic.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
				HandleMoveAutocomplete(ctx, state, ic)
			} else {
				err = HandleMove(ctx, state, ic)
			}
		case "view":
			err = HandleView(ctx, state, ic)
		case "analyze":
			err = HandleAnalyze(ctx, state, ic)
		case "simulate":
			err = HandleSimulate(ctx, state, ic)
		case "stats":
			err = HandleStats(ctx, state, ic)
		case "leaderboard":
			err = HandleLeaderboard(ctx, state, ic)
		}
		// if a handler returns an error, it should not have sent an interaction response yet
		if err != nil {
			handleInteractionError(ctx, state.Dg, ic, err)
		}
	case discordgo.InteractionMessageComponent:
		msg := ic.MessageComponentData()
		slog.Info("received a message component", "name", msg.CustomID)

		cond, key := parseCustomId(msg.CustomID)

		switch cond {
		case SimPauseKey:
			err = HandlePauseComponent(state, ic, key)
		case SimStopKey:
			err = HandleStopComponent(state, ic, key)
		default:
			slog.Warn("unknown message component condition", "name", msg.CustomID, "cond", cond)
		}
		// if a handler returns an error, it should not have sent an interaction response yet
		if err != nil {
			handleInteractionError(ctx, state.Dg, ic, err)
		}
	}
}

var ChallengeSubCmds = []string{"bot", "user"}

func HandleChallenge(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	subCmd, options := getSubcommand(ic)
	switch subCmd {
	case "bot":
		return HandleBotChallengeCommand(ctx, state, ic, options)
	case "user":
		return HandleUserChallengeCommand(ctx, state, ic, options)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func HandleBotChallengeCommand(ctx context.Context, state *State, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
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

	game, err := CreateBotGame(ctx, state.Db, player, level)
	if err == ErrAlreadyPlaying {
		interactionRespond(state.Dg, ic.Interaction, createStringResponse("You're already in a game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create game with level=%d, player=%v cmd: %s", level, player, err)
	}

	embed := createGameStartEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func HandleUserChallengeCommand(ctx context.Context, state *State, ic *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := getPlayerOpt(ctx, &state.UserCache, options, "opponent")
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
		if _, err = state.Dg.ChannelMessageSend(channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID)); err != nil {
			slog.Error("failed to send user challenge expire", "err", err)
		}
	}
	state.ChallengeCache.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, handleExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.ID, player.Name, player.ID)

	interactionRespond(state.Dg, ic.Interaction, createStringResponse(msg))
	return nil
}

func HandleAccept(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	cmd := ic.ApplicationCommandData()
	player := MakeHumanPlayer(ic.Interaction.Member.User)

	opponent, err := getPlayerOpt(ctx, &state.UserCache, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %s", err)
	}

	didAccept := state.ChallengeCache.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		interactionRespond(state.Dg, ic.Interaction, createStringResponse("Cannot accept a challenge that does not exist."))
		return nil
	}
	game, err := CreateGame(ctx, state.Db, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create OthelloGame with opponent=%v cmd: %s", opponent, err)
	}

	embed := createGameStartEmbed(game)
	img := state.Renderer.DrawBoard(game.Board)

	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func HandleView(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, state.Db, user.ID)
	if err == ErrGameNotFound {
		interactionRespond(state.Dg, ic.Interaction, createStringResponse("You're not playing a OthelloGame."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get OthelloGame for player=%s: %s", user.ID, err)
	}

	embed := createGameEmbed(game)
	img := state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())

	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func HandleForfeit(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, state.Db, user.ID)
	if err == ErrGameNotFound {
		interactionRespond(state.Dg, ic.Interaction, createStringResponse("You're not currently in a Game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get Game for player=%s: %s", user.ID, err)
	}
	if err := DeleteGame(ctx, state.Db, game); err != nil {
		return fmt.Errorf("failed to delete Game in forfeit: %s", err)
	}

	gameResult := game.CreateForfeitResult(user.ID)
	statsResult, err := UpdateStats(ctx, state.Db, gameResult)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %s", user.ID, err)
	}

	embed := createForfeitEmbed(gameResult, statsResult)
	img := state.Renderer.DrawBoard(game.Board)

	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
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

	interactionRespond(state.Dg, ic.Interaction, createAutocompleteResponse(choices))
}

func handleGameOver(ctx context.Context, state *State, game OthelloGame, move Tile) (*discordgo.MessageEmbed, image.Image, error) {
	var gameResult GameResult
	var statsResult StatsResult

	gameResult = game.CreateResult()
	statsResult, err := UpdateStats(ctx, state.Db, gameResult)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to update stats for result=%v: %s", gameResult, err)
	}

	embed := createGameOverEmbed(game, gameResult, statsResult, move)
	img := state.Renderer.DrawBoard(game.Board)
	return embed, img, nil
}

func HandleMove(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
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

	game, err := MakeMoveValidated(ctx, state.Db, player.ID, move)

	if resp := createMoveErrorResp(err, moveStr); resp != nil {
		interactionRespond(state.Dg, ic.Interaction, resp)
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to make move=%v for player=%s: %s", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = handleGameOver(ctx, state, game, move); err != nil {
			return fmt.Errorf("failed to handle Game over while handling Moves: %s", err)
		}
	} else {
		img = state.Renderer.DrawBoardMoves(game.Board, game.Board.FindCurrentMoves())
		if game.CurrentPlayer().IsHuman() {
			embed = createGameMoveEmbed(game, move)
		} else {
			embed = createGameEmbed(game)
			state.TaskCh <- BotTask{game: game}
		}
	}

	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, img))
	return nil
}

func HandleAnalyze(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	trace := ctx.Value(TraceKey)

	ctx, cancel := context.WithTimeout(ctx, time.Minute*2)
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

	game, err := GetGame(ctx, state.Db, user.ID)
	if err == ErrGameNotFound {
		interactionRespond(state.Dg, ic.Interaction, createStringResponse("You're not currently in a OthelloGame."))
		return nil
	}

	interactionRespond(state.Dg, ic.Interaction, createStringResponse("Analyzing... Wait a second..."))

	respCh := state.Sh.FindRankedMoves(game, LevelToDepth(level))
	select {
	case resp := <-respCh:
		if resp.Ok {
			embed := createAnalysisEmbed(game, level)
			img := state.Renderer.DrawBoardAnalysis(game.Board, resp.Moves)
			interactionResponseEdit(state.Dg, ic.Interaction, createEmbedEdit(embed, img))
		} else {
			interactionResponseEdit(state.Dg, ic.Interaction, createEmbedTextEdit("Failed to retrieve analysis data from engine."))
		}
	case <-ctx.Done():
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		interactionResponseEdit(state.Dg, ic.Interaction, createStringEdit("Timed out while waiting for a response."))
	}
	return nil
}

func HandleSimulate(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	ctx, cancel := context.WithTimeout(ctx, time.Hour*1) // a simulation can stay paused for up to an hour
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
	img := state.Renderer.DrawBoard(initialGame.Board)

	simulationID := uuid.New().String()

	response := createComponentResponse(embed, img, createSimulationActionRow(simulationID, false))
	interactionRespond(state.Dg, ic.Interaction, response)

	// run the simulation against the engine and add it to the cache (so it can be paused/resumed)
	simState := &SimState{Cancel: cancel}
	simChan := make(chan SimStep, MaxSimCount) // give this a size so we don't block on send

	state.SimCache.Set(simulationID, simState, SimulationTtl)

	go GenerateSimulation(ctx, state.Sh, initialGame, simChan)
	RecvSimulation(ctx, state, ic, delay, simState, simChan)

	return nil
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
			interactionResponseEdit(state.Dg, ic.Interaction, createStepEdit(state.Renderer, step))
		}
	}
}

func HandleStats(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	var user discordgo.User
	var err error

	userOpt := ic.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		if user, err = state.UserCache.GetUser(ctx, userOpt.Value.(string)); err != nil {
			return fmt.Errorf("failed to get user while handling stats: %s", err)
		}
	} else if ic.Interaction.Member != nil {
		user = *ic.Interaction.Member.User
	}

	var stats Stats
	if stats, err = ReadStats(ctx, state.Db, state.UserCache, user.ID); err != nil {
		return fmt.Errorf("failed to read stats while handling stats: %s", err)
	}

	embed := createStatsEmbed(user, stats)
	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, nil))
	return nil
}

const LeaderboardSize = 50

func HandleLeaderboard(ctx context.Context, state *State, ic *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, state.Db, state.UserCache, LeaderboardSize); err != nil {
		return fmt.Errorf("failed to top stats while handling leaderboard: %s", err)
	}

	embed := createLeaderboardEmbed(stats)
	interactionRespond(state.Dg, ic.Interaction, createEmbedResponse(embed, nil))
	return nil
}

func HandlePauseComponent(state *State, ic *discordgo.InteractionCreate, simulationID string) error {
	acknowledge := func() {
		interactionRespond(state.Dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := state.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return nil
	}

	simulationID = item.Key()
	simState := item.Value()

	isPaused := !simState.IsPaused.Toggle() // negate this because it returns the old value

	acknowledge()

	components := createSimulationActionRow(simulationID, isPaused)
	interactionResponseEdit(state.Dg, ic.Interaction, &discordgo.WebhookEdit{Components: &components})
	return nil
}

func HandleStopComponent(state *State, ic *discordgo.InteractionCreate, simulationID string) error {
	acknowledge := func() {
		interactionRespond(state.Dg, ic.Interaction, &discordgo.InteractionResponse{Type: discordgo.InteractionResponseUpdateMessage})
	}

	item := state.SimCache.Get(simulationID)
	if item == nil {
		acknowledge()
		return nil
	}

	simState := item.Value()
	simState.Cancel()

	acknowledge()
	return nil
}

func HandleBotMove(ctx context.Context, state *State, task BotTask, moves []RankTile) {
	game, err := MakeMoveValidated(ctx, state.Db, task, move) // OthelloGame will be stored at the ID of the player that is NOT the service
	if err != nil {
		return
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			slog.Error("failed to send game over service move", "err", err)
		}
	} else {
		embed = createGameMoveEmbed(game, move)
		img = h.Renderer.DrawBoardMoves(game.OthelloBoard, game.LoadPotentialMoves())
	}
	if _, err := dg.ChannelMessageSendComplex(channelID, createEmbedSend(embed, img)); err != nil {
		slog.Error("failed to send service move", "err", err)
	}

	if !game.IsGameOver() && game.CurrentPlayer().IsHuman() {
		h.handleBotMove(ctx, dg, channelID, game)
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
